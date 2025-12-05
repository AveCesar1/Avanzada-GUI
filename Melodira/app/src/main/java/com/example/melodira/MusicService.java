package com.example.melodira;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicService extends Service {

    public static final String ACTION_TRACK_CHANGED = "com.example.melodira.TRACK_CHANGED";
    public static final String ACTION_QUEUE_UPDATED = "com.example.melodira.QUEUE_UPDATED";

    // Acciones para los botones de la notificación
    public static final String ACTION_PLAY = "com.example.melodira.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.melodira.ACTION_PAUSE";
    public static final String ACTION_NEXT = "com.example.melodira.ACTION_NEXT";
    public static final String ACTION_PREV = "com.example.melodira.ACTION_PREV";

    private static final String CHANNEL_ID = "MusicChannel";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer player;
    private List<Track> playlist = new ArrayList<>();
    private int currentIndex = -1;

    private List<Track> playbackOrder;
    private boolean isShuffle = false;
    private boolean isRepeatOne = false;

    private MediaSessionCompat mediaSession;
    private android.media.AudioManager audioManager;
    private android.media.AudioFocusRequest focusRequest; // Para Android 8.0+
    private boolean resumeOnFocusGain = false; // ¿Debemos reanudar si nos interrumpieron momentáneamente?

    public class LocalBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        player = new MediaPlayer();

        // Inicializar MediaSession
        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { play(); }
            @Override
            public void onPause() { pause(); }
            @Override
            public void onSkipToNext() { next(); }
            @Override
            public void onSkipToPrevious() { prev(); }
            @Override
            public void onSeekTo(long pos) { seekTo((int) pos); }
        });
        mediaSession.setActive(true);

        player.setOnCompletionListener(mp -> {
            playTrack(getNextIndex());
        });

        // Registrar el receptor para los botones de la notificación
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREV);
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED);

        createNotificationChannel();
    }

    // Receptor para clicks en la notificación
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case ACTION_PLAY: play(); break;
                case ACTION_PAUSE: pause(); break;
                case ACTION_NEXT: next(); break;
                case ACTION_PREV: prev(); break;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Manejo de botones multimedia de auriculares (opcional pero recomendado)
        if (intent != null) {
            androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setQueue(List<Track> newQueue) {
        this.playlist = newQueue;
        this.playbackOrder = new ArrayList<>(newQueue);
        this.isShuffle = false;
    }

    public List<Track> getQueue() {
        return playlist;
    }

    public void playTrack(int index) {
        if (playlist == null || playlist.isEmpty()) return;
        if (index < 0 || index >= playlist.size()) return;

        // 1. PEDIR FOCO ANTES DE TODO
        if (!requestAudioFocus()) {
            return; // Si Spotify no nos deja, no tocamos.
        }

        currentIndex = index;
        Track track = playlist.get(index);

        try {
            player.reset();
            Log.d("MusicService", "Intentando reproducir: " + track.path);

            if (track.path.startsWith("content://")) {
                player.setDataSource(getApplicationContext(), Uri.parse(track.path));
            } else {
                player.setDataSource(track.path);
            }

            player.prepare();
            player.start();

            showNotification(track);
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING); // Actualizar estado para la barra
            sendBroadcast(new Intent(ACTION_TRACK_CHANGED));

        } catch (Exception e) {
            Log.e("MusicService", "Error FATAL al reproducir: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void play() {
        // 1. PEDIR FOCO
        if (!requestAudioFocus()) {
            return;
        }

        if (player != null && !player.isPlaying()) {
            // Preparamos volumen en 0 antes de arrancar
            player.setVolume(0.0f, 0.0f);
            player.start();

            // Fade-in rápido (500ms o medio segundo)
            fadeIn();

            updateNotificationState();
            updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
            sendBroadcast(new Intent(ACTION_TRACK_CHANGED));
        }
    }

    private void fadeIn() {
        ValueAnimator fadeAnim = ValueAnimator.ofFloat(0.0f, 1.0f);
        fadeAnim.setDuration(800); // 0.8 segundos para entrar suave
        fadeAnim.addUpdateListener(animation -> {
            if (player != null) {
                float volume = (float) animation.getAnimatedValue();
                player.setVolume(volume, volume);
            }
        });
        fadeAnim.start();
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            // En lugar de pausar de golpe, iniciamos el fade-out
            fadeOutAndPause();
        }
    }

    private void fadeOutAndPause() {
        // Animamos de 1.0 (Volumen Máximo) a 0.0 (Silencio)
        ValueAnimator fadeAnim = ValueAnimator.ofFloat(1.0f, 0.0f);
        fadeAnim.setDuration(1500); // 1.5 segundos de duración

        fadeAnim.addUpdateListener(animation -> {
            if (player != null) {
                float volume = (float) animation.getAnimatedValue();
                player.setVolume(volume, volume);
            }
        });

        fadeAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Solo pausamos cuando el volumen ya llegó a 0
                if (player != null) {
                    player.pause();
                    // IMPORTANTE: Resetear volumen al máximo para la próxima vez que demos Play
                    player.setVolume(1.0f, 1.0f);

                    // Actualizamos notificaciones y estado
                    updateNotificationState();
                    updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED);
                    sendBroadcast(new Intent(ACTION_TRACK_CHANGED));
                }
            }
        });

        fadeAnim.start();
    }

    public void next() {
        if (playlist.isEmpty()) return;
        playTrack(getNextIndex());
    }

    public void prev() {
        if (playlist.isEmpty()) return;
        playTrack(getPrevIndex());
    }

    public void seekTo(int pos) {
        if (player != null) {
            player.seekTo(pos);
            // Actualizar la barra de progreso de la notificación
            updateMediaSessionState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
        }
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

    // --- ACTUALIZACIÓN DE LA SESIÓN (Para la barra de progreso) ---
    private void updateMediaSessionState(int state) {
        if (mediaSession == null) return;

        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state, getPosition(), 1.0f); // Posición actual y velocidad (1.0x)

        mediaSession.setPlaybackState(playbackStateBuilder.build());

        // Actualizar metadatos (Duración, título, etc.) para la pantalla de bloqueo
        Track track = getCurrentTrack();
        if (track != null) {
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());

            Bitmap art = getAlbumArt(track.path);
            if (art != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
            }

            mediaSession.setMetadata(metadataBuilder.build());
        }
    }

    // --- NOTIFICACIONES MEJORADAS (MEDIA STYLE) ---

    private void showNotification(Track track) {
        updateMediaSessionState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);

        // Intent para abrir la app al tocar la notif
        Intent notIntent = new Intent(this, NowPlayingActivity.class);
        notIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notIntent, PendingIntent.FLAG_IMMUTABLE);

        // Botón Prev
        Intent prevIntent = new Intent(ACTION_PREV);
        PendingIntent prevPending = PendingIntent.getBroadcast(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE);

        // Botón Play/Pause
        Intent playPauseIntent = new Intent(isPlaying() ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePending = PendingIntent.getBroadcast(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE);
        int playPauseIcon = isPlaying() ? R.drawable.ic_pause_minimal : R.drawable.ic_play_minimal;

        // Botón Next
        Intent nextIntent = new Intent(ACTION_NEXT);
        PendingIntent nextPending = PendingIntent.getBroadcast(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE);

        Bitmap largeIcon = getAlbumArt(track.path);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setSmallIcon(R.drawable.ic_note_minimal)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentIntent)
                .setDeleteIntent(androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo

                // --- AQUÍ ESTÁ LA MAGIA DEL MEDIA STYLE ---
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken()) // Conectar con MediaSession
                        .setShowActionsInCompactView(0, 1, 2)) // Mostrar Prev(0), Play(1), Next(2) en vista compacta

                .addAction(new NotificationCompat.Action(R.drawable.ic_prev_minimal, "Previous", prevPending))
                .addAction(new NotificationCompat.Action(playPauseIcon, "Play/Pause", playPausePending))
                .addAction(new NotificationCompat.Action(R.drawable.ic_next_minimal, "Next", nextPending));

        startForeground(1, builder.build());
    }

    private void updateNotificationState() {
        if (currentIndex != -1 && currentIndex < playlist.size()) {
            showNotification(playlist.get(currentIndex));
        }
    }

    // Helper para obtener carátula (reciclado de tu código de adapter)
    private Bitmap getAlbumArt(String path) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            if (path.startsWith("content://")) {
                retriever.setDataSource(getApplicationContext(), Uri.parse(path));
            } else {
                retriever.setDataSource(path);
            }
            byte[] art = retriever.getEmbeddedPicture();
            retriever.release();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Retorna null si no hay imagen, Android usará el icono pequeño o gris por defecto
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reproducción de Música",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controles del reproductor en la barra de estado");
            channel.setShowBadge(false); // No mostrar puntito en el icono de la app
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

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
        if (mediaSession != null) {
            mediaSession.release();
        }
        unregisterReceiver(notificationReceiver);
    }

    // --- RESTO DE MÉTODOS (Shuffle, Repeat, getNextIndex, etc.) IGUAL QUE ANTES ---
    public boolean toggleShuffle() {
        isShuffle = !isShuffle;
        if (playlist == null || playlist.isEmpty()) return isShuffle;
        if (isShuffle) {
            playbackOrder = new ArrayList<>(playlist);
            Collections.shuffle(playbackOrder);
        } else {
            playbackOrder = new ArrayList<>(playlist);
        }
        updateNotificationState();
        return isShuffle;
    }

    public boolean isShuffleEnabled() { return isShuffle; }

    public boolean toggleRepeat() {
        isRepeatOne = !isRepeatOne;
        return isRepeatOne;
    }

    public boolean isRepeatOneEnabled() { return isRepeatOne; }

    public int getNextIndex() {
        if (playlist == null || playlist.isEmpty()) return -1;
        if (isRepeatOne) return currentIndex;
        if (playbackOrder.isEmpty()) return -1;

        Track currentTrack = playlist.get(currentIndex);
        int currentPositionInPlaybackOrder = playbackOrder.indexOf(currentTrack);
        int nextPositionInPlaybackOrder = currentPositionInPlaybackOrder + 1;
        if (nextPositionInPlaybackOrder >= playbackOrder.size()) {
            nextPositionInPlaybackOrder = 0;
        }
        Track nextTrack = playbackOrder.get(nextPositionInPlaybackOrder);
        return playlist.indexOf(nextTrack);
    }

    public int getPrevIndex() {
        if (playbackOrder.isEmpty()) return -1;
        Track currentTrack = playlist.get(currentIndex);
        int currentPositionInPlaybackOrder = playbackOrder.indexOf(currentTrack);
        int prevPositionInPlaybackOrder = currentPositionInPlaybackOrder - 1;
        if (prevPositionInPlaybackOrder < 0) {
            prevPositionInPlaybackOrder = playbackOrder.size() - 1;
        }
        Track prevTrack = playbackOrder.get(prevPositionInPlaybackOrder);
        return playlist.indexOf(prevTrack);
    }

    public List<Track> getPlaybackOrder() {
        if (playbackOrder == null) return playlist;
        return playbackOrder;
    }

    public Track getCurrentTrack() {
        if (playlist != null && currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    // --- GESTIÓN DE AUDIO FOCUS ---

    private final android.media.AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case android.media.AudioManager.AUDIOFOCUS_LOSS:
                // Pérdida permanente (ej: Spotify empezó a tocar).
                // Acción: Pausar y NO reanudar automáticamente.
                if (isPlaying()) {
                    pause();
                    resumeOnFocusGain = false;
                }
                break;

            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pérdida temporal (ej: Entra una llamada o Google Assistant habla).
                // Acción: Pausar, pero recordar que queremos volver.
                if (isPlaying()) {
                    pause();
                    resumeOnFocusGain = true;
                }
                break;

            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Pérdida temporal permitiendo bajar volumen (ej: Notificación de WhatsApp).
                // Acción: Bajar volumen (Ducking).
                if (player != null) {
                    player.setVolume(0.2f, 0.2f);
                }
                break;

            case android.media.AudioManager.AUDIOFOCUS_GAIN:
                // Recuperamos el foco (terminó la llamada o la notificación).
                if (player != null) {
                    // Restaurar volumen si hicimos ducking
                    player.setVolume(1.0f, 1.0f);
                }

                // Si nos pausaron temporalmente (TRANSIENT), reanudamos
                if (resumeOnFocusGain) {
                    play();
                    resumeOnFocusGain = false;
                }
                break;
        }
    };

    private boolean requestAudioFocus() {
        int result;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Para Android 8.0 (Oreo) y superior
            android.media.AudioAttributes playbackAttributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();

            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            // Para versiones viejas (Legacy)
            result = audioManager.requestAudioFocus(focusChangeListener,
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.AUDIOFOCUS_GAIN);
        }

        return result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
}
