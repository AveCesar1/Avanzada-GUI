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

        // CAMBIO: Pide la lista en el orden de reproducción actual.
        List<Track> orderedQueue = musicService.getPlaybackOrder();

        // El resto sigue igual, pero usando la nueva lista ordenada.
        adapter = new MusicAdapter(orderedQueue, musicService.getCurrentTrack(), this); // Pasamos el objeto Track
        rv.setAdapter(adapter);

        // Opcional: Hacer scroll a la canción actual
        int currentPosInOrderedList = orderedQueue.indexOf(musicService.getCurrentTrack());
        if(currentPosInOrderedList != -1) {
            rv.scrollToPosition(currentPosInOrderedList);
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (!bound || musicService == null) return;

        // 1. Obtiene la canción del playbackOrder según la posición en la que se hizo clic.
        Track clickedTrack = musicService.getPlaybackOrder().get(position);

        // 2. Encuentra el índice REAL de esa canción en la cola original.
        int originalIndex = musicService.getQueue().indexOf(clickedTrack);

        // 3. Reproduce usando el índice original.
        if(originalIndex != -1) {
            musicService.playTrack(originalIndex);
        }

        // 4. Actualiza visualmente cuál está seleccionada
        adapter.setSelected(clickedTrack);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-configura la lista entera por si el orden (shuffle) cambió.
        if (bound && musicService != null) {
            setupList();
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
