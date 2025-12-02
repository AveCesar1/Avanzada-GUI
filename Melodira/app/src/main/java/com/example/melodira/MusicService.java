package com.example.melodira;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {

    public static final String ACTION_TRACK_CHANGED = "com.example.melodira.TRACK_CHANGED";
    public static final String ACTION_QUEUE_UPDATED = "com.example.melodira.QUEUE_UPDATED";
    private static final String CHANNEL_ID = "MusicChannel";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer player;
    private List<Track> playlist = new ArrayList<>();
    private int currentIndex = -1;

    // Variables para el control de flujo
    private boolean isShuffle = false;
    private boolean isRepeatOne = false; // false = Playlist, true = Canción

    public class LocalBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        player = new MediaPlayer();

        // Configurar listener para cuando termine una canción
        // CAMBIO: Usamos playTrack con getNextIndex() para respetar el modo aleatorio/repetir
        player.setOnCompletionListener(mp -> {
            playTrack(getNextIndex());
        });

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Si el sistema mata el servicio, no lo reinicies automáticamente sin intención
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // --- CONTROL DE REPRODUCCIÓN ---

    public void setQueue(List<Track> tracks) {
        this.playlist = tracks;
        // Avisamos a la UI que la lista cambió
        sendBroadcast(new Intent(ACTION_QUEUE_UPDATED));
    }

    public List<Track> getQueue() {
        return playlist;
    }

    public void playTrack(int index) {
        if (playlist == null || playlist.isEmpty()) return;
        if (index < 0 || index >= playlist.size()) return;

        currentIndex = index;
        Track track = playlist.get(index);

        try {
            player.reset(); // Reiniciar estado (Crucial para evitar error -38)

            Log.d("MusicService", "Intentando reproducir: " + track.path);

            // --- AQUÍ ESTÁ LA CORRECCIÓN MÁGICA ---
            if (track.path.startsWith("content://")) {
                // Es una URI (versión antigua o SAF)
                player.setDataSource(getApplicationContext(), Uri.parse(track.path));
            } else {
                // Es una ruta de archivo normal (/storage/...) (TU VERSIÓN ACTUAL)
                player.setDataSource(track.path);
            }
            // ---------------------------------------

            player.prepare(); // Preparamos síncronamente (más seguro para archivos locales)
            player.start();

            showNotification(track);
            sendBroadcast(new Intent(ACTION_TRACK_CHANGED));

        } catch (Exception e) {
            Log.e("MusicService", "Error FATAL al reproducir: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void play() {
        if (player != null && !player.isPlaying()) {
            player.start();
            updateNotificationState(); // Actualizar notif
            sendBroadcast(new Intent(ACTION_TRACK_CHANGED)); // Actualizar UI
        }
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
            updateNotificationState();
            sendBroadcast(new Intent(ACTION_TRACK_CHANGED));
        }
    }

    public void next() {
        if (playlist.isEmpty()) return;
        int nextIndex = currentIndex + 1;
        if (nextIndex >= playlist.size()) nextIndex = 0; // Loop
        playTrack(nextIndex);
    }

    public void prev() {
        if (playlist.isEmpty()) return;
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = playlist.size() - 1;
        playTrack(prevIndex);
    }

    public void seekTo(int pos) {
        if (player != null) player.seekTo(pos);
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public int getDuration() {
        return (player != null) ? player.getDuration() : 0;
    }

    public int getPosition() {
        return (player != null) ? player.getCurrentPosition() : 0;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    // --- NOTIFICACIONES ---

    private void showNotification(Track track) {
        Intent notIntent = new Intent(this, NowPlayingActivity.class);
        notIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // Importante para no duplicar activity

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setSmallIcon(R.drawable.ic_note_minimal) // Asegúrate de tener este icono
                .setContentIntent(contentIntent)
                .setOngoing(true) // No se puede quitar con swipe mientras suena
                .build();

        startForeground(1, notification);
    }

    private void updateNotificationState() {
        if (currentIndex != -1 && currentIndex < playlist.size()) {
            showNotification(playlist.get(currentIndex));
            // Nota: Si pausas, podrías hacer setOngoing(false) aquí si quisieras que se pueda quitar
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reproducción de Música",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // --- MÉTODOS DE CONTROL NUEVOS ---

    public boolean toggleShuffle() {
        isShuffle = !isShuffle;
        return isShuffle;
    }

    public boolean isShuffleEnabled() {
        return isShuffle;
    }

    public boolean toggleRepeat() {
        isRepeatOne = !isRepeatOne;
        return isRepeatOne;
    }

    public boolean isRepeatOneEnabled() {
        return isRepeatOne;
    }

    // Lógica inteligente para obtener la siguiente canción
    public int getNextIndex() {
        if (playlist == null || playlist.isEmpty()) return -1;

        // 1. Si es "Repetir Uno"
        if (isRepeatOne) {
            return currentIndex; // Devuelve la misma
        }

        // 2. Si es "Aleatorio"
        if (isShuffle) {
            // Retorna un número al azar entre 0 y el tamaño de la lista - 1
            return new java.util.Random().nextInt(playlist.size());
        }

        // 3. Comportamiento Normal (Repetir Playlist)
        int next = currentIndex + 1;
        if (next >= playlist.size()) {
            next = 0; // Volver al inicio (bucle infinito de playlist)
        }
        return next;
    }

    public int getPrevIndex() {
        if (playlist == null || playlist.isEmpty()) return -1;

        // Si es aleatorio o repetir uno, al dar "Anterior" solemos querer ir al principio de la canción
        // o a una anterior lógica, pero simplifiquemos:
        int prev = currentIndex - 1;
        if (prev < 0) {
            prev = playlist.size() - 1;
        }
        return prev;
    }
}
