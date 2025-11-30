package com.example.melodira;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.VH> implements SimpleItemTouchHelperCallback.ItemTouchHelperAdapter {

    public interface OnItemClick { void onItemClicked(int position); }

    private List<Track> items;
    private int selected = -1;
    private OnItemClick listener;

    // --- OPTIMIZACIÓN: Caché y Hilos ---
    // Guardamos las imágenes en memoria para no cargarlas mil veces
    private LruCache<String, Bitmap> memoryCache;
    // Un ejecutor para cargar imágenes en segundo plano sin congelar la app
    private ExecutorService executorService = Executors.newFixedThreadPool(4);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public MusicAdapter(List<Track> list, int selectedIndex, OnItemClick l) {
        items = list;
        selected = selectedIndex;
        listener = l;

        // Configuramos la memoria caché (usamos 1/8 de la memoria disponible)
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Track t = items.get(position);

        holder.tvTitle.setText(t.title != null ? t.title : "Sin Título");
        holder.tvArtist.setText(t.artist != null ? t.artist : "Desconocido");

        String imageKey = t.path;
        Bitmap cachedBitmap = getBitmapFromMemCache(imageKey);

        if (cachedBitmap != null) {
            // A. TIENE PORTADA REAL EN CACHÉ
            holder.ivMiniCover.setImageBitmap(cachedBitmap);
            holder.ivMiniCover.clearColorFilter();

            // ESTILO FULL: Sin padding y recorta para llenar
            holder.ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.ivMiniCover.setPadding(0, 0, 0, 0);

        } else {
            // B. NO TIENE PORTADA (Es el ícono por defecto)
            holder.ivMiniCover.setImageResource(R.drawable.ic_note_minimal);
            holder.ivMiniCover.setColorFilter(android.graphics.Color.DKGRAY);

            // ESTILO ÍCONO: Pequeño, centrado y con padding para que respire
            holder.ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int padding = 20; // Ajusta este número si quieres el ícono más grande o chico
            holder.ivMiniCover.setPadding(padding, padding, padding, padding);

            // Mandamos a buscar la imagen real en segundo plano
            loadCoverAsync(holder, t.path, position);
        }

        // --- SELECCIÓN ---
        if (position == selected) {
            holder.card.setCardBackgroundColor(0xFFEAEAEA);
        } else {
            holder.card.setCardBackgroundColor(0x00000000);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(holder.getAdapterPosition());
            setSelected(holder.getAdapterPosition());
        });
    }

    // Metodo para cargar en segundo plano
    private void loadCoverAsync(VH holder, String path, int position) {
        // Guardamos la posición actual en el tag para verificar después si la vista se recicló
        holder.ivMiniCover.setTag(path);

        executorService.execute(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(path);
                byte[] data = mmr.getEmbeddedPicture();
                if (data != null) {
                    // Truco de optimización: Cargarla reducida (thumbnail) no tamaño completo
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);

                    // Calculamos reducción para que sea pequeña (aprox 50x50px)
                    options.inSampleSize = calculateInSampleSize(options, 60, 60);
                    options.inJustDecodeBounds = false;

                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                }
            } catch (Exception e) {
                // Fallo silencioso, se queda el icono por defecto
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }

            // Si encontramos imagen, la guardamos y la mostramos
            if (bitmap != null) {
                addBitmapToMemoryCache(path, bitmap);

                Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> {
                    if (path.equals(holder.ivMiniCover.getTag())) {
                        holder.ivMiniCover.setImageBitmap(finalBitmap);
                        holder.ivMiniCover.clearColorFilter();

                        // --- AGREGA ESTO ---
                        // Al llegar la imagen, la expandimos y quitamos el padding
                        holder.ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        holder.ivMiniCover.setPadding(0, 0, 0, 0);
                        // -------------------
                    }
                });
            }
        });
    }

    // Métodos de ayuda para el Caché
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null && bitmap != null) {
            memoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return memoryCache.get(key);
    }

    // Método matemático para reducir la imagen
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    public void setSelected(int idx) {
        int prev = selected;
        selected = idx;
        if (prev >= 0) notifyItemChanged(prev);
        notifyItemChanged(idx);
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(items, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void onItemDismiss(int position) {
        items.remove(position);
        notifyItemRemoved(position);
    }

    public static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvTitle, tvArtist;
        ImageView ivMiniCover;

        public VH(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            tvTitle = v.findViewById(R.id.tvItemTitle);
            tvArtist = v.findViewById(R.id.tvItemArtist);
            ivMiniCover = v.findViewById(R.id.ivMiniCover);
        }
    }
}
