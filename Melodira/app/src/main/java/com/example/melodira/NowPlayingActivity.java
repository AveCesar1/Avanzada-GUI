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
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;

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
    private static final int REQ_ALL_FILES_ACCESS = 101;

    private MusicService musicService;
    private boolean isBound = false;
    private List<Track> pendingTracks = null; // <--- ESTA ES LA CLAVE
    private boolean bound = false;
    private Handler handler = new Handler();

    private TextView tvTitle, tvArtist, tvAlbum, tvTimeCur, tvTimeTotal;
    private SeekBar seekBar;
    private ImageButton btnPlay, btnPrev, btnNext;
    private ImageView ivCover;

    private boolean fromUser = false;

    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            Log.d("DEBUG_APP", "Broadcast recibido: " + intent.getAction());

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

    // Busca esto al inicio de tu clase y reemplázalo:
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            // Verificamos DIRECTAMENTE el servicio, sin variables 'bound' confusas
            if (musicService != null && musicService.isPlaying()) {
                long pos = musicService.getPosition();
                long dur = musicService.getDuration();

                // Actualizamos la UI
                updateSeekUi(pos, dur);
            }

            // Se vuelve a llamar a sí mismo cada 500ms (medio segundo)
            // Lo ponemos FUERA del if para que el bucle nunca muera mientras la app esté abierta
            handler.postDelayed(this, 500);
        }
    };


    /*
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
     */

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // En onCreate de NowPlayingActivity

        ImageButton btnPrev = findViewById(R.id.btnPrev);
        ImageButton btnNext = findViewById(R.id.btnNext);

        // BOTÓN SIGUIENTE
        btnNext.setOnClickListener(v -> {
            if (musicService != null) {
                List<Track> queue = musicService.getQueue();
                if (queue != null && !queue.isEmpty()) {
                    int nextIndex = musicService.getCurrentIndex() + 1;

                    // Si llegamos al final, volvemos al principio (0)
                    if (nextIndex >= queue.size()) {
                        nextIndex = 0;
                    }

                    musicService.playTrack(nextIndex);
                    refreshMetadata(); // Actualizamos la pantalla de inmediato
                }
            }
        });

        // BOTÓN ANTERIOR
        btnPrev.setOnClickListener(v -> {
            if (musicService != null) {
                List<Track> queue = musicService.getQueue();
                if (queue != null && !queue.isEmpty()) {
                    int prevIndex = musicService.getCurrentIndex() - 1;

                    // Si estamos en el principio y damos atrás, vamos a la última canción
                    if (prevIndex < 0) {
                        prevIndex = queue.size() - 1;
                    }

                    musicService.playTrack(prevIndex);
                    refreshMetadata(); // Actualizamos la pantalla de inmediato
                }
            }
        });


        ImageButton btnOpenPlaylist = findViewById(R.id.btnOpenPlaylist);
        ImageButton btnGoToPlayer = findViewById(R.id.btnGoToPlayer);


        // Ir a Playlist
        btnOpenPlaylist.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlaylistActivity.class); // O como se llame tu activity de lista
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // <--- TRUCO DE MAGIA
            startActivity(intent);
        });

        // Ir al Reproductor (Ya estamos aquí, así que no hace nada o muestra un mensajito)
        btnGoToPlayer.setOnClickListener(v -> {
            // Opcional: Toast.makeText(this, "Ya estás en el reproductor", Toast.LENGTH_SHORT).show();
        });


        tvTitle = findViewById(R.id.tvTitle);
        tvArtist = findViewById(R.id.tvArtist);
        tvAlbum = findViewById(R.id.tvAlbum);
        tvTimeCur = findViewById(R.id.tvTimeCur);
        tvTimeTotal = findViewById(R.id.tvTimeTotal);
        seekBar = findViewById(R.id.seekBar);
        btnPlay = findViewById(R.id.btnPlay);
        ivCover = findViewById(R.id.ivCover);

        btnPlay.setOnClickListener(v -> {
            // CORRECCIÓN: Ignoramos 'bound' o 'isBound' y preguntamos directo al objeto
            if (musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                    // Actualizamos icono inmediatamente para feedback visual rápido
                    btnPlay.setImageResource(R.drawable.ic_play_minimal);
                } else {
                    musicService.play();
                    btnPlay.setImageResource(R.drawable.ic_pause_minimal);
                }
                // Sincronizamos el resto de la UI
                refreshMetadata();
            }
        });

        // ... dentro de onCreate, reemplaza el listener del seekBar ...

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Si el usuario lo está moviendo, actualizamos el texto del tiempo en vivo
                if (fromUser) {
                    tvTimeCur.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                fromUser = true; // Pausamos la actualización automática del reloj
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // CORRECCIÓN: Verificamos el objeto musicService directamente
                if (musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
                fromUser = false; // Reanudamos el reloj automático
            }
        });


        findViewById(R.id.btnOpenPlaylist).setOnClickListener(v -> startActivity(new Intent(this, PlaylistActivity.class)));

        // registrar receiver
        IntentFilter f = new IntentFilter();
        f.addAction(MusicService.ACTION_TRACK_CHANGED);
        f.addAction(MusicService.ACTION_QUEUE_UPDATED);
        ContextCompat.registerReceiver(this, serviceReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);

        // START: permission + service binding flow
        // Chequeo para Android 11 (R) o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // CORRECCIÓN: Usamos android.os.Environment directamente
            if (android.os.Environment.isExternalStorageManager()) {
                // ¡Ya tenemos permiso total! Arrancamos.
                startAndBindService();
            } else {
                // No tenemos permiso, enviamos al usuario a la pantalla de configuración especial
                try {
                    // CORRECCIÓN: Usamos android.provider.Settings directamente
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, REQ_ALL_FILES_ACCESS);
                    Toast.makeText(this, "Por favor, concede el permiso 'Permitir acceso a todos los archivos' para leer tu música.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQ_ALL_FILES_ACCESS);
                }
            }
        } else {
            // Para Android 10 o inferior (Metodo antiguo)
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

    }

    // Así debe quedar startAndBindService
    private void startAndBindService() {
        Intent svc = new Intent(this, MusicService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        // CAMBIO AQUÍ: Usamos 'serviceConnection' (la variable del final)
        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        handler.post(updateRunnable);
    }


    /**
     * Si el servicio ya está conectado, le pasamos la lista encontrada y reproducimos la primera pista.
     * Si no hay pistas y no hay permisos, lanzamos SAF para que el usuario seleccione la carpeta.
     */
    private void loadTracksAndMaybePlay() {
        Toast.makeText(this, "Escaneando música...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            // 1. Escaneo en segundo plano (rápido)
            List<Track> tracks = MusicScanner.scanDownloadsAndDocuments(this);

            runOnUiThread(() -> {
                if (tracks == null || tracks.isEmpty()) {
                    Toast.makeText(this, "No se encontraron canciones MP3.", Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(this, "¡Encontradas " + tracks.size() + " canciones!", Toast.LENGTH_SHORT).show();

                // 2. INTENTO DE REPRODUCCIÓN
                if (musicService != null && isBound) {
                    // Escenario A: El servicio YA está listo -> Reproducimos directo
                    musicService.setQueue(tracks);
                    musicService.playTrack(0);
                } else {
                    // Escenario B: El servicio NO está listo -> Guardamos en espera
                    Log.d("DEBUG_APP", "Servicio no listo. Guardando en pendingTracks.");
                    pendingTracks = tracks;

                    // Nos aseguramos de intentar conectar el servicio
                    startAndBindService();
                }
            });
        }).start();
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
        if (requestCode == REQ_ALL_FILES_ACCESS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permiso concedido. Escaneando...", Toast.LENGTH_SHORT).show();
                    // CAMBIO: Llamamos a la función que hace el escaneo en segundo plano
                    loadTracksAndMaybePlay();
                } else {
                    Toast.makeText(this, "Permiso denegado.", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
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
        if (musicService == null) return;

        List<Track> q = musicService.getQueue();
        int idx = musicService.getCurrentIndex();

        if (q == null || q.isEmpty()) return;
        if (idx < 0 || idx >= q.size()) idx = 0;

        Track t = q.get(idx);

        runOnUiThread(() -> {
            // 1. Textos
            tvTitle.setText(t.title != null ? t.title : "Desconocido");
            tvArtist.setText(t.artist != null ? t.artist : "Artista Desconocido");
            if (tvAlbum != null) tvAlbum.setText(t.album != null ? t.album : "");

            // 2. Seekbar y Tiempos
            long dur = t.durationMs > 0 ? t.durationMs : musicService.getDuration();
            long pos = musicService.getPosition();
            if (dur > 0) {
                seekBar.setMax((int) dur);
                seekBar.setProgress((int) pos);
                tvTimeTotal.setText(formatTime(dur)); // Asegúrate de actualizar el texto total
            }
            tvTimeCur.setText(formatTime(pos));

            // 3. Botón Play/Pause
            if (btnPlay != null) {
                btnPlay.setImageResource(musicService.isPlaying() ?
                        R.drawable.ic_pause_minimal : R.drawable.ic_play_minimal);
            }

            // 4. --- CARGA DE PORTADA (NUEVO) ---
            // Usamos un hilo de fondo rápido para no congelar la UI decodificando la imagen
            new Thread(() -> {
                android.graphics.Bitmap artBitmap = null;
                try {
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    // Importante: Usar el URI parseado
                    mmr.setDataSource(this, Uri.parse(t.path));

                    byte[] artBytes = mmr.getEmbeddedPicture();
                    if (artBytes != null) {
                        artBitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
                    }
                    mmr.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Volvemos al hilo principal para pintar la imagen
                android.graphics.Bitmap finalArt = artBitmap;
                // Dentro de refreshMetadata -> runOnUiThread de la imagen...

                runOnUiThread(() -> {
                    if (ivCover != null) {
                        if (finalArt != null) {
                            // CASO 1: Hay portada real
                            // Cambiamos a centerCrop para que la foto llene el cuadro
                            ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            ivCover.setPadding(0, 0, 0, 0); // Quitamos el padding
                            ivCover.setImageBitmap(finalArt);
                        } else {
                            // CASO 2: No hay portada (Usar Nota Musical)
                            int padding = (int) (80 * getResources().getDisplayMetrics().density);
                            ivCover.setPadding(padding, padding, padding, padding);

                            ivCover.setImageResource(R.drawable.ic_note_minimal);
                        }

                    }
                });
            }).start();
            // -----------------------------------
        });
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
            unbindService(serviceConnection);
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
            isBound = true; // (o 'bound = true' si usas esa variable)

            Log.d("DEBUG_APP", "Servicio conectado. Revisando pendientes...");

            // AQUÍ ESTÁ LA MAGIA:
            // Si teníamos canciones esperando (porque el permiso se dio apenas), las cargamos ahora.
            if (pendingTracks != null && !pendingTracks.isEmpty()) {
                Log.d("DEBUG_APP", "Cargando " + pendingTracks.size() + " canciones de la lista de espera.");

                musicService.setQueue(pendingTracks);
                musicService.playTrack(0); // Esto inicia la reproducción automáticamente

                pendingTracks = null; // Limpiamos la lista de espera
            }

            // Actualizamos la pantalla (título, artista, etc.)
            refreshMetadata();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
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

        // AGREGAR ESTO: Iniciar el reloj
        handler.removeCallbacks(updateRunnable); // Por seguridad para no duplicar
        handler.post(updateRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // AGREGAR ESTO: Detener el reloj para ahorrar batería
        handler.removeCallbacks(updateRunnable);

        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}
