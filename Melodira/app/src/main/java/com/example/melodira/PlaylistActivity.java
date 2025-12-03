package com.example.melodira;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistActivity extends AppCompatActivity implements MusicAdapter.OnItemClick {

    private MusicService musicService;
    private boolean bound = false;
    private RecyclerView rv;
    private MusicAdapter adapter;

    // --- VARIABLES PARA FAST SCROLL ---
    private LinearLayout indexContainer;
    private Map<String, Integer> sectionPositions = new HashMap<>();
    private List<String> indexItems; // Lista mixta (String letras + Icono estrella)
    // ----------------------------------

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

        // --- NAVEGACIÓN ---
        ImageButton btnOpenPlaylist = findViewById(R.id.btnOpenPlaylist);
        ImageButton btnGoToPlayer = findViewById(R.id.btnGoToPlayer);
        btnOpenPlaylist.setOnClickListener(v -> {});
        btnGoToPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(this, NowPlayingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        // --- SETUP UI ---
        rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));

        indexContainer = findViewById(R.id.indexContainer);

        // CAMBIO 2: Definimos la estructura del índice: #, A-Z, y un marcador para la Estrella
        String[] alphabet = {"#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "STAR_ICON"};
        indexItems = new ArrayList<>(Arrays.asList(alphabet));

        bindService(new Intent(this, MusicService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private void setupList() {
        if (!bound || musicService == null) return;

        List<Track> orderedQueue = musicService.getPlaybackOrder();
        adapter = new MusicAdapter(orderedQueue, musicService.getCurrentTrack(), this);
        rv.setAdapter(adapter);

        int currentPos = orderedQueue.indexOf(musicService.getCurrentTrack());
        if(currentPos != -1) rv.scrollToPosition(currentPos);

        // 1. Calcular posiciones (Lógica actualizada)
        calculateSectionPositions(orderedQueue);
        // 2. Dibujar índice (Con icono XML y #)
        setupIndexUI();
    }

    // --- LOGICA FAST SCROLL MEJORADA ---

    private void calculateSectionPositions(List<Track> tracks) {
        sectionPositions.clear();

        for (int i = 0; i < tracks.size(); i++) {
            String title = tracks.get(i).title;
            if (title == null || title.isEmpty()) continue;

            String firstChar = title.substring(0, 1).toUpperCase();
            String sectionKey;

            if (firstChar.matches("[A-Z]")) {
                // Es letra latina
                sectionKey = firstChar;
            } else if (firstChar.matches("[0-9]") || !Character.isLetterOrDigit(firstChar.charAt(0))) {
                // Es número o símbolo -> Agrupar en "#"
                sectionKey = "#";
            } else {
                // Es otro alfabeto (Kanji, Cirílico, etc) -> Agrupar en Estrella
                sectionKey = "STAR_ICON";
            }

            // Guardamos solo la primera aparición
            if (!sectionPositions.containsKey(sectionKey)) {
                sectionPositions.put(sectionKey, i);
            }
        }
    }

    private void setupIndexUI() {
        indexContainer.removeAllViews();

        // Configuración de LayoutParams para distribuir equitativamente
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );

        for (String item : indexItems) {
            if (item.equals("STAR_ICON")) {
                // CAMBIO 1: Si es la estrella, inflamos una ImageView con tu drawable
                ImageView iv = new ImageView(this);
                iv.setImageResource(R.drawable.ic_star_index); // <--- Tu nuevo XML
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iv.setLayoutParams(params);
                iv.setPadding(4, 4, 4, 4); // Ajusta padding si es necesario
                indexContainer.addView(iv);
            } else {
                // Si es letra o #, usamos TextView
                TextView tv = new TextView(this);
                tv.setText(item);
                tv.setTextSize(10);
                tv.setTextColor(Color.DKGRAY);
                tv.setGravity(Gravity.CENTER);
                tv.setLayoutParams(params);
                indexContainer.addView(tv);
            }
        }

        indexContainer.setOnTouchListener((v, event) -> {
            handleIndexTouch(event);
            return true;
        });
    }

    private void handleIndexTouch(MotionEvent event) {
        float y = event.getY();
        float height = indexContainer.getHeight();
        if (height == 0) return;

        int action = event.getAction();

        // Si el usuario suelta el dedo, reiniciamos el estilo
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            indexContainer.setBackgroundColor(Color.TRANSPARENT);
            resetIndexStyles(); // <--- NUEVO: Volver todo a la normalidad
            return;
        }

        // Calcular índice
        int positionIndex = (int) ((y / height) * indexItems.size());
        if (positionIndex < 0) positionIndex = 0;
        if (positionIndex >= indexItems.size()) positionIndex = indexItems.size() - 1;

        // --- EFECTO LUPA ---
        // Recorremos todas las letras para "encender" la seleccionada y "apagar" las demás
        for (int i = 0; i < indexContainer.getChildCount(); i++) {
            View child = indexContainer.getChildAt(i);

            if (i == positionIndex) {
                // ES LA SELECCIONADA:
                // 1. Hacemos que crezca (Escala 1.8x)
                child.animate().scaleX(1.8f).scaleY(1.8f).setDuration(0).start();
                // 2. La movemos un poco a la izquierda para que no se corte
                child.setTranslationX(-20f);

                // 3. Cambio de color y estilo
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(Color.BLACK);
                    ((TextView) child).setTypeface(null, android.graphics.Typeface.BOLD);
                } else if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(Color.BLACK);
                }

            } else {
                // NO ES LA SELECCIONADA (Restaurar o hacer pequeñito)
                child.animate().scaleX(1.0f).scaleY(1.0f).setDuration(0).start();
                child.setTranslationX(0f);

                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(Color.LTGRAY); // Un gris más claro para los no seleccionados
                    ((TextView) child).setTypeface(null, android.graphics.Typeface.NORMAL);
                } else if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(Color.LTGRAY);
                }
            }
        }

        // Lógica de Scroll (Igual que antes)
        String selectedKey = indexItems.get(positionIndex);
        if (sectionPositions.containsKey(selectedKey)) {
            int targetPosition = sectionPositions.get(selectedKey);
            // Usamos el scroll optimizado (el cheat)
            smoothScrollToPosition(targetPosition);
        }
    }

    // Nuevo método para limpiar el diseño al soltar
    private void resetIndexStyles() {
        for (int i = 0; i < indexContainer.getChildCount(); i++) {
            View child = indexContainer.getChildAt(i);

            // Restaurar animación suavemente
            child.animate().scaleX(1.0f).scaleY(1.0f).translationX(0).setDuration(150).start();

            if (child instanceof TextView) {
                ((TextView) child).setTextColor(Color.DKGRAY); // Color original
                ((TextView) child).setTypeface(null, android.graphics.Typeface.NORMAL);
            } else if (child instanceof ImageView) {
                ((ImageView) child).clearColorFilter(); // O ponerle el tinte original
            }
        }
    }



    // Metodo optimizado para listas largas (>1000 items)
    private void smoothScrollToPosition(int position) {
        RecyclerView.LayoutManager layoutManager = rv.getLayoutManager();

        if (layoutManager instanceof LinearLayoutManager) {
            // CHEAT DE DISEÑO:
            // En lugar de deslizar por 1000 items (que traba el celular),
            // saltamos directamente a la posición.
            // El cerebro lo percibe como "instantáneo" y es mucho más eficiente.

            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 0);
        }
    }


    // ----------------------------------------------

    @Override
    public void onItemClicked(int position) {
        if (!bound || musicService == null) return;
        Track clickedTrack = musicService.getPlaybackOrder().get(position);
        int originalIndex = musicService.getQueue().indexOf(clickedTrack);
        if(originalIndex != -1) {
            musicService.playTrack(originalIndex);
        }
        adapter.setSelected(clickedTrack);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
