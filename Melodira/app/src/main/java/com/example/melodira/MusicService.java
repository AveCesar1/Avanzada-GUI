package com.example.melodira;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicService extends Service {

    private static final String TAG = "MusicService";

    public static final String CHANNEL_ID = "melodira_channel";
    public static final String ACTION_TRACK_CHANGED = "com.example.melodira.ACTION_TRACK_CHANGED";
    public static final String ACTION_QUEUE_UPDATED = "com.example.melodira.ACTION_QUEUE_UPDATED";
    public static final String EXTRA_INDEX = "extra_index";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer player;
    private List<Track> queue = new ArrayList<>();
    private int index = 0;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 none, 1 all, 2 one

    public class LocalBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        player = new MediaPlayer();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
        }
        player.setOnCompletionListener(mp -> onTrackComplete());
        player.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "MediaPlayer error what=" + what + " extra=" + extra);
            return true; // manejar error internamente
        });
        player.setOnPreparedListener(mp -> {
            try {
                mp.start();
            } catch (IllegalStateException ise) {
                Log.w(TAG, "Error al iniciar reproducción: " + ise.getMessage());
            }
            // notificar que la pista arrancó
            sendTrackChangedBroadcast();
            startForegroundNotificationForCurrent();
        });
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Melodira playback", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private void startForegroundNotificationForCurrent() {
        if (queue == null || queue.isEmpty() || index < 0 || index >= queue.size()) return;
        Track t = queue.get(index);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(t.title)
                .setContentText(t.artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private void stopForegroundQuietly() {
        try {
            stopForeground(true);
        } catch (Exception ignored) {}
    }

    /**
     * Reemplaza la cola completa. Emite ACTION_QUEUE_UPDATED.
     */
    public void setQueue(List<Track> list) {
        if (list == null) list = new ArrayList<>();
        queue = new ArrayList<>(list);
        if (index >= queue.size()) index = 0;
        sendQueueUpdatedBroadcast();
    }

    public List<Track> getQueue() {
        return queue;
    }

    public void playTrack(int pos) {
        if (queue == null || queue.isEmpty()) return;
        if (pos < 0) pos = 0;
        if (pos >= queue.size()) pos = queue.size() - 1;
        index = pos;
        playCurrent();
    }

    private void playCurrent() {
        if (queue == null || queue.isEmpty()) return;
        Track t = queue.get(index);
        if (t == null) return;

        // --- AÑADE ESTO PARA VERIFICAR ---
        Log.d("DEBUG_APP", "MusicService intenta reproducir: " + t.title);
        Log.d("DEBUG_APP", "Ruta (Path/URI): " + t.path);
        // ---------------------------------

        player.reset();

        try {
            String path = t.path;
            if (path == null) path = "";

            // Detectar si es URI content:// o file://
            if (path.startsWith("content://") || path.startsWith("file://") || path.startsWith("android.resource://")) {
                Uri uri = Uri.parse(path);
                // use setDataSource(context, uri)
                player.setDataSource(getApplicationContext(), uri);
            } else if (path.startsWith("/")) {
                // ruta absoluta en filesystem
                player.setDataSource(path);
            } else if (path.contains("://")) {
                // otro esquema improbable pero intentarlo como URI
                Uri uri = Uri.parse(path);
                player.setDataSource(getApplicationContext(), uri);
            } else {
                // como fallback tratar como file path
                player.setDataSource(path);
            }

            // preparar async para que onPrepared arranque y notifique
            player.prepareAsync();

        } catch (Exception e) {
            Log.w(TAG, "Error setting data source for path=" + t.path + " : " + e.getMessage());
            // Intentar fallback con ParcelFileDescriptor si es URI content
            try {
                if (t.path != null && t.path.startsWith("content://")) {
                    Uri uri = Uri.parse(t.path);
                    ContentResolver cr = getContentResolver();
                    ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        FileDescriptor fd = pfd.getFileDescriptor();
                        player.reset();
                        player.setDataSource(fd);
                        pfd.close();
                        player.prepareAsync();
                        return;
                    }
                }
            } catch (Exception ex) {
                Log.w(TAG, "Fallback PFD failed: " + ex.getMessage());
            }
            // notificar fallo (no lanzar crash)
            sendTrackChangedBroadcast(); // notificar, aunque no esté reproduciendo
        }
    }

    public void play() {
        if (player != null && !player.isPlaying()) {
            try { player.start(); } catch (Exception e) { Log.w(TAG, "play error: " + e.getMessage()); }
            sendTrackChangedBroadcast();
        }
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
            sendTrackChangedBroadcast();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getDuration() {
        if (player == null) return 0;
        try { return player.getDuration(); } catch (Exception e) { return 0; }
    }

    public long getPosition() {
        if (player == null) return 0;
        try { return player.getCurrentPosition(); } catch (Exception e) { return 0; }
    }

    public void seekTo(int ms) {
        if (player != null) {
            try { player.seekTo(ms); } catch (Exception e) { Log.w(TAG, "seek error: " + e.getMessage()); }
        }
    }

    public int getCurrentIndex() { return index; }

    public void next() {
        if (queue == null || queue.isEmpty()) return;
        if (isShuffle) {
            index = (int) (Math.random() * queue.size());
        } else {
            index++;
            if (index >= queue.size()) {
                if (repeatMode == 1) index = 0; // repeat all
                else {
                    index = queue.size() - 1;
                    pause();
                    return;
                }
            }
        }
        playCurrent();
    }

    public void prev() {
        if (queue == null || queue.isEmpty()) return;
        index--;
        if (index < 0) {
            if (repeatMode == 1) index = queue.size() - 1;
            else index = 0;
        }
        playCurrent();
    }

    private void onTrackComplete() {
        if (repeatMode == 2) {
            playCurrent(); // repeat one
        } else {
            next();
        }
    }

    public void setShuffle(boolean s) {
        isShuffle = s;
    }

    public void setRepeatMode(int mode) {
        repeatMode = mode;
    }

    /**
     * Reordena la cola (drag & drop). Ajusta índice actual. Emite ACTION_QUEUE_UPDATED.
     */
    public void moveItem(int from, int to) {
        if (queue == null) return;
        if (from < 0 || to < 0 || from >= queue.size() || to >= queue.size()) return;
        Track t = queue.remove(from);
        queue.add(to, t);

        if (index == from) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;

        sendQueueUpdatedBroadcast();
    }

    private void sendTrackChangedBroadcast() {
        Intent i = new Intent(ACTION_TRACK_CHANGED);
        i.putExtra(EXTRA_INDEX, index);
        if (queue != null && index >= 0 && index < queue.size()) {
            Track t = queue.get(index);
            i.putExtra("title", t.title);
            i.putExtra("artist", t.artist);
            i.putExtra("duration", t.durationMs);
            i.putExtra("isPlaying", isPlaying());
        }
        try { sendBroadcast(i); } catch (Exception ignored) {}
    }

    private void sendQueueUpdatedBroadcast() {
        Intent i = new Intent(ACTION_QUEUE_UPDATED);
        try { sendBroadcast(i); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {}
        stopForegroundQuietly();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Puedes poner manejo de acciones (play/pause/next) desde notificación aquí.
        return START_STICKY;
    }
}
