package com.example.melodira;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

public class NowPlayingActivity extends AppCompatActivity {

    private static final int REQ_READ = 1001;
    private static final int REQ_OPEN_TREE = 2001;

    private MusicService musicService;
    private boolean isBound = false;
    private List<Track> pendingTracks = null; // <--- ESTA ES LA CLAVE
    private boolean bound = false;
    private Handler handler = new Handler();

    private TextView tvTitle, tvArtist, tvAlbum, tvTimeCur, tvTimeTotal;
    private SeekBar seekBar;
    private ImageButton btnPlay, btnPrev, btnNext;
    private ImageView ivCover;


    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (action.equals(MusicService.ACTION_TRACK_CHANGED)) {
                // actualiza metadatos y seekbar
                refreshMetadata();
            } else if (action.equals(MusicService.ACTION_QUEUE_UPDATED)) {
                // la cola cambió; refrescar vista
                refreshMetadata();
            }
        }
    };

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (bound && musicService != null && musicService.isPlaying()) {
                long pos = musicService.getPosition();
                long dur = musicService.getDuration();
                updateSeekUi(pos, dur);
            }
            handler.postDelayed(this, 500);
        }
    };

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MusicService.LocalBinder lb = (MusicService.LocalBinder) iBinder;
            musicService = lb.getService();
            bound = true;
            // setQueue and start playback if we already have tracks
            loadTracksAndMaybePlay();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTitle = findViewById(R.id.tvTitle);
        tvArtist = findViewById(R.id.tvArtist);
        tvAlbum = findViewById(R.id.tvAlbum);
        tvTimeCur = findViewById(R.id.tvTimeCur);
        tvTimeTotal = findViewById(R.id.tvTimeTotal);
        seekBar = findViewById(R.id.seekBar);
        btnPlay = findViewById(R.id.btnPlay);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        ivCover = findViewById(R.id.ivCover);

        btnPlay.setOnClickListener(v -> {
            if (bound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                    btnPlay.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    musicService.play();
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                }
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (bound && musicService != null) {
                musicService.prev();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (bound && musicService != null) {
                musicService.next();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean fromUser = false;
            @Override public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) { fromUser = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (bound && musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
                fromUser = false;
            }
        });

        findViewById(R.id.btnOpenPlaylist).setOnClickListener(v -> startActivity(new Intent(this, PlaylistActivity.class)));

        // registrar receiver
        IntentFilter f = new IntentFilter();
        f.addAction(MusicService.ACTION_TRACK_CHANGED);
        f.addAction(MusicService.ACTION_QUEUE_UPDATED);
        ContextCompat.registerReceiver(this, serviceReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);

        // START: permission + service binding flow
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startAndBindService();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
            }
        } else {
            startAndBindService();
        }
    }

    private void startAndBindService() {
        Intent svc = new Intent(this, MusicService.class);
        startService(svc);
        bindService(new Intent(this, MusicService.class), conn, Context.BIND_AUTO_CREATE);
        handler.post(updateRunnable);
    }

    /**
     * Si el servicio ya está conectado, le pasamos la lista encontrada y reproducimos la primera pista.
     * Si no hay pistas y no hay permisos, lanzamos SAF para que el usuario seleccione la carpeta.
     */
    private void loadTracksAndMaybePlay() {
        if (!bound || musicService == null) return;

        // 1) intenta scan por MediaStore o SAF guardado
        List<Track> tracks = MusicScanner.scanDownloadsAndDocuments(this);

        if (tracks == null || tracks.isEmpty()) {
            // No encontramos nada. Intentaremos pedir al usuario seleccionar la carpeta (SAF)
            Toast.makeText(this, "No se encontraron .mp3 automáticamente. Selecciona la carpeta Downloads o Documents para dar acceso.", Toast.LENGTH_LONG).show();
            launchFolderPicker();
            return;
        }

        musicService.setQueue(tracks);
        musicService.playTrack(0); // reproducirá cuando se prepare y enviará broadcast
        // no llamar refreshMetadata() aquí: la actualización llegará vía broadcast cuando la pista esté preparada
    }

    private void launchFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"));
            }
            startActivityForResult(intent, REQ_OPEN_TREE);
        } catch (Exception e) {
            Toast.makeText(this, "No es posible abrir selector de carpetas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_TREE) {
            if (data != null) {
                Uri treeUri = data.getData();
                if (treeUri != null) {
                    try {
                        // Pasamos las constantes directamente para satisfacer la validación
                        getContentResolver().takePersistableUriPermission(treeUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception ignored) {

                    }
                    MusicScanner.saveTreeUri(this, treeUri);
                    List<Track> safTracks = MusicScanner.scanDocumentTree(this, treeUri);

                    // AÑADIR ESTO:
                    if (safTracks == null) {
                        Log.d("DEBUG_APP", "scanDocumentTree devolvió NULL");
                    } else {
                        Log.d("DEBUG_APP", "scanDocumentTree encontró " + safTracks.size() + " canciones.");
                    }

                    if (safTracks != null && !safTracks.isEmpty()) {
                        Log.d("DEBUG_APP", "Canciones encontradas: " + safTracks.size());

                        if (musicService != null && isBound) {
                            // Escenario A: El servicio ya está listo
                            musicService.setQueue(safTracks);
                            musicService.playTrack(0);
                        } else {
                            // Escenario B: El servicio no está listo (tu error actual)
                            Log.d("DEBUG_APP", "El servicio no está listo, guardando en pendingTracks...");
                            pendingTracks = safTracks;
                            // El onServiceConnected se encargará de reproducirlas apenas termine de conectar
                        }
                    }
                }
            }
        }
    }

    private void refreshMetadata() {
        if (!bound || musicService == null) return;
        int idx = musicService.getCurrentIndex();
        List<Track> q = musicService.getQueue();
        if (q == null || q.isEmpty() || idx < 0 || idx >= q.size()) return;
        Track t = q.get(idx);

        tvTitle.setText(t.title != null ? t.title : "Desconocido");
        tvArtist.setText(t.artist != null ? t.artist : "");
        tvAlbum.setText(t.album != null ? t.album : "");

        long dur = t.durationMs > 0 ? t.durationMs : musicService.getDuration();
        updateSeekUi(musicService.getPosition(), dur);
        if (dur > Integer.MAX_VALUE) dur = Integer.MAX_VALUE;
        seekBar.setMax((int) dur);
        btnPlay.setImageResource(musicService.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    private void updateSeekUi(long pos, long dur) {
        tvTimeCur.setText(formatTime(pos));
        tvTimeTotal.setText(formatTime(dur));
        if (dur > 0) seekBar.setProgress((int) pos);
    }

    private String formatTime(long ms) {
        int totalSecs = (int) (ms / 1000);
        int min = totalSecs / 60;
        int sec = totalSecs % 60;
        return String.format(Locale.getDefault(), "%d:%02d", min, sec);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
        try { unregisterReceiver(serviceReceiver); } catch (Exception ignored) {}
        if (bound) {
            unbindService(conn);
            bound = false;
        }
    }

    // resultado de permiso runtime
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_READ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAndBindService();
            } else {
                Toast.makeText(this, "Permiso de almacenamiento denegado. Selecciona manualmente la carpeta con tus MP3.", Toast.LENGTH_LONG).show();
                launchFolderPicker();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            Log.d("DEBUG_APP", "Servicio conectado correctamente.");

            // AQUÍ ESTÁ LA MAGIA: Si había canciones esperando, las tocamos ahora
            if (pendingTracks != null && !pendingTracks.isEmpty()) {
                Log.d("DEBUG_APP", "Procesando lista pendiente de " + pendingTracks.size() + " canciones.");
                musicService.setQueue(pendingTracks);
                musicService.playTrack(0);
                pendingTracks = null; // Limpiamos la lista de espera
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
            Log.d("DEBUG_APP", "Servicio desconectado.");
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // Iniciamos y vinculamos el servicio
        Intent intent = new Intent(this, MusicService.class);
        // startService es necesario para que la música siga sonando si cierras la app
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}
