package com.example.melodira;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// Asegúrate de que implemente la interfaz de tu adaptador (MusicAdapter o TrackAdapter)
public class PlaylistActivity extends AppCompatActivity implements MusicAdapter.OnItemClick {

    private MusicService musicService;
    private boolean bound = false;
    private RecyclerView rv;
    private MusicAdapter adapter; // O TrackAdapter, según como se llame tu archivo

    private ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.LocalBinder lb = (MusicService.LocalBinder) binder;
            musicService = lb.getService();
            bound = true;
            setupList();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        // --- NAVEGACIÓN INFERIOR ---
        ImageButton btnOpenPlaylist = findViewById(R.id.btnOpenPlaylist);
        ImageButton btnGoToPlayer = findViewById(R.id.btnGoToPlayer);

        // Botón Playlist (No hace nada porque ya estamos aquí)
        btnOpenPlaylist.setOnClickListener(v -> {});

        // Botón para volver al Reproductor
        btnGoToPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(this, NowPlayingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        // ---------------------------

        // CAMBIO: Usamos el ID nuevo del XML (recyclerView) en lugar de rvTracks
        rv = findViewById(R.id.recyclerView);

        // NOTA: Borramos btnShuffle, btnRepeat, tvPos y btnBack porque
        // ya no existen en el nuevo diseño minimalista.

        rv.setLayoutManager(new LinearLayoutManager(this));

        bindService(new Intent(this, MusicService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private void setupList() {
        if (!bound || musicService == null) return;
        List<Track> q = musicService.getQueue();

        // Crea el adaptador
        adapter = new MusicAdapter(q, musicService.getCurrentIndex(), this);
        rv.setAdapter(adapter);

        // Opcional: Si quitaste el drag & drop (ItemTouchHelper) para simplificar,
        // borra las líneas de ItemTouchHelper. Si lo quieres mantener,
        // asegúrate de que SimpleItemTouchHelperCallback siga existiendo.
    }

    @Override
    public void onItemClicked(int position) {
        if (!bound || musicService == null) return;

        // Reproducir la canción seleccionada
        musicService.playTrack(position);

        // Actualizar visualmente cuál está seleccionada
        adapter.setSelected(position);

        // Opcional: Volver al reproductor automáticamente al tocar una canción
        // Intent intent = new Intent(this, NowPlayingActivity.class);
        // intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        // startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refrescar la lista al volver (por si cambió la canción en la otra pantalla)
        if (adapter != null && musicService != null) {
            adapter.setSelected(musicService.getCurrentIndex());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(conn);
            bound = false;
        }
    }
}
