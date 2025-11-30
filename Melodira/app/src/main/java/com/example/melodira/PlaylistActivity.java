package com.example.melodira;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PlaylistActivity extends AppCompatActivity implements MusicAdapter.OnItemClick {

    private MusicService musicService;
    private boolean bound = false;
    private RecyclerView rv;
    private MusicAdapter adapter;
    private ImageButton btnShuffle, btnRepeat;
    private TextView tvPos;

    private ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.LocalBinder lb = (MusicService.LocalBinder) binder;
            musicService = lb.getService();
            bound = true;
            setupList();
        }
        @Override public void onServiceDisconnected(ComponentName name) { bound = false; musicService = null; }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        rv = findViewById(R.id.rvTracks);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        tvPos = findViewById(R.id.tvPos);

        rv.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindService(new Intent(this, MusicService.class), conn, Context.BIND_AUTO_CREATE);

        btnShuffle.setOnClickListener(v -> {
            if (!bound) return;
            musicService.setShuffle(!musicService.isPlaying()); // silly toggle; better track via state
        });

        btnRepeat.setOnClickListener(v -> {
            if (!bound) return;
            musicService.setRepeatMode((musicService.getCurrentIndex()+1)%3); // quick cycle (demo)
        });
    }

    private void setupList() {
        if (!bound || musicService == null) return;
        List<Track> q = musicService.getQueue();
        adapter = new MusicAdapter(q, musicService.getCurrentIndex(), this);
        rv.setAdapter(adapter);

        ItemTouchHelper.Callback cb = new SimpleItemTouchHelperCallback(adapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(cb);
        touchHelper.attachToRecyclerView(rv);
    }

    @Override
    public void onItemClicked(int position) {
        if (!bound || musicService == null) return;
        musicService.playTrack(position);
        adapter.setSelected(position);
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
