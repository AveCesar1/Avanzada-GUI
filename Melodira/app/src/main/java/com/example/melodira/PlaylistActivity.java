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

    // --- VARIABLES BÚSQUEDA ---
    private CardView searchContainer;
    private android.widget.EditText etSearch;
    private ImageButton btnSearchIcon;
    private List<Track> originalQueue; // Para guardar la lista completa
    private boolean isSearchOpen = false;

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

        // --- SETUP BÚSQUEDA ---
        searchContainer = findViewById(R.id.searchContainer);
        etSearch = findViewById(R.id.etSearch);
        btnSearchIcon = findViewById(R.id.btnSearchIcon);

        // 1. Abrir/Cerrar buscador al tocar la lupa
        btnSearchIcon.setOnClickListener(v -> toggleSearch());

        // 2. Filtrar mientras escribes
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

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

        // Guardamos la lista original si es la primera vez o si no estamos buscando
        if (originalQueue == null || !isSearchOpen) {
            originalQueue = new ArrayList<>(musicService.getPlaybackOrder());
        }

        // Inicialmente mostramos la lista original
        adapter = new MusicAdapter(originalQueue, musicService.getCurrentTrack(), this);
        rv.setAdapter(adapter);

        int currentPos = originalQueue.indexOf(musicService.getCurrentTrack());
        if(currentPos != -1) rv.scrollToPosition(currentPos);

        // Calculamos índice para la lista completa
        calculateSectionPositions(originalQueue);
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

        // Obtenemos la canción correcta (incluso si estamos filtrando)
        Track clickedTrack = adapter.getItem(position);

        // Buscamos su índice REAL en la cola de reproducción del servicio
        int originalIndex = musicService.getQueue().indexOf(clickedTrack);

        if(originalIndex != -1) {
            // 1. Ordenamos al servicio que toque la música
            musicService.playTrack(originalIndex);

            // 2. ¡MAGIA! Nos teletransportamos automáticamente al reproductor
            Intent intent = new Intent(this, NowPlayingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // Para no crear duplicados
            startActivity(intent);
        }

        // Esto ya no es estrictamente necesario si nos vamos de la pantalla,
        // pero está bien dejarlo por si vuelves atrás.
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

    // --------------------------------------------

    // --- LÓGICA DEL BUSCADOR ---

    private void toggleSearch() {
        if (isSearchOpen) {
            // CERRAR: Ocultar barra, limpiar texto y cerrar teclado
            isSearchOpen = false;
            searchContainer.setVisibility(View.GONE);
            etSearch.setText(""); // Esto disparará filterList("") y restaurará la lista

            // Cerrar teclado
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);

        } else {
            // ABRIR: Mostrar barra y enfocar
            isSearchOpen = true;
            searchContainer.setVisibility(View.VISIBLE); // Aparece la barra
            searchContainer.setAlpha(0f);
            searchContainer.animate().alpha(1f).setDuration(200).start(); // Fade in suave

            etSearch.requestFocus();

            // Abrir teclado automáticamente
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void filterList(String query) {
        if (originalQueue == null) return;

        List<Track> filteredList = new ArrayList<>();

        // Si está vacío, mostramos todo
        if (query.isEmpty()) {
            filteredList.addAll(originalQueue);
        } else {
            String lowerQuery = query.toLowerCase().trim();

            for (Track track : originalQueue) {
                // Buscamos en Título, Artista o Álbum
                boolean matchesTitle = track.title != null && track.title.toLowerCase().contains(lowerQuery);
                boolean matchesArtist = track.artist != null && track.artist.toLowerCase().contains(lowerQuery);
                boolean matchesAlbum = track.album != null && track.album.toLowerCase().contains(lowerQuery);

                if (matchesTitle || matchesArtist || matchesAlbum) {
                    filteredList.add(track);
                }
            }
        }

        // Actualizamos el adaptador con la nueva lista filtrada
        // NOTA: Pasamos null como currentTrack temporalmente para evitar saltos visuales raros al filtrar
        adapter = new MusicAdapter(filteredList, musicService.getCurrentTrack(), this);
        rv.setAdapter(adapter);

        // Opcional: Recalcular el índice lateral para la lista filtrada (o ocultarlo si son pocos items)
        if (filteredList.size() < 10) {
            indexContainer.setVisibility(View.GONE); // Ocultar índice si hay pocos resultados
        } else {
            indexContainer.setVisibility(View.VISIBLE);
            calculateSectionPositions(filteredList); // Recalcular índice para lo filtrado
            setupIndexUI(); // Redibujar letras
        }
    }
}
