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

    // --- CORRECCIÓN 1: Variable correcta para guardar la canción seleccionada ---
    private Track selectedTrack;
    private OnItemClick listener;

    // --- OPTIMIZACIÓN: Caché y Hilos ---
    private LruCache<String, Bitmap> memoryCache;
    private ExecutorService executorService = Executors.newFixedThreadPool(4);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // --- CORRECCIÓN 2: El constructor recibe Track y lo guarda en la variable correcta ---
    public MusicAdapter(List<Track> list, Track currentTrack, OnItemClick l) {
        items = list;
        this.selectedTrack = currentTrack; // Guardamos el objeto Track
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
            holder.ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.ivMiniCover.setPadding(0, 0, 0, 0);

        } else {
            // B. NO TIENE PORTADA
            holder.ivMiniCover.setImageResource(R.drawable.ic_note_minimal);
            holder.ivMiniCover.setColorFilter(android.graphics.Color.DKGRAY);
            holder.ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int padding = 20;
            holder.ivMiniCover.setPadding(padding, padding, padding, padding);

            loadCoverAsync(holder, t.path, position);
        }

        // --- CORRECCIÓN 3: Comparamos Objetos, no números ---
        // Si la canción de esta fila (t) es igual a la seleccionada (selectedTrack)
        if (selectedTrack != null && t.equals(selectedTrack)) {
            holder.card.setCardBackgroundColor(0xFFEAEAEA); // Gris seleccionado
        } else {
            holder.card.setCardBackgroundColor(0x00000000); // Transparente
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(holder.getAdapterPosition());
            // Actualizamos visualmente pasando el objeto Track
            setSelected(t);
        });
    }

    // --- CORRECCIÓN 4: setSelected ahora acepta Track ---
    public void setSelected(Track track) {
        this.selectedTrack = track;
        notifyDataSetChanged(); // Refresca la lista para pintar el nuevo seleccionado
    }

    // Metodo para cargar en segundo plano
    private void loadCoverAsync(VH holder, String path, int position) {
        holder.ivMiniCover.setTag(path);

        executorService.execute(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(path);
                byte[] data = mmr.getEmbeddedPicture();
                if (data != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);

                    options.inSampleSize = calculateInSampleSize(options, 60, 60);
                    options.inJustDecodeBounds = false;

                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                }
            } catch (Exception e) {
                // Fallo silencioso
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }

            if (bitmap != null) {
                addBitmapToMemoryCache(path, bitmap);
                Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> {
                    if (path.equals(holder.ivMiniCover.getTag())) {
                        holder.ivMiniCover.setImageBitmap(finalBitmap);
                        holder.ivMiniCover.clearColorFilter();
                        holder.ivMiniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        holder.ivMiniCover.setPadding(0, 0, 0, 0);
                    }
                });
            }
        });
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null && bitmap != null) {
            memoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return memoryCache.get(key);
    }

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

    // En MusicAdapter.java
    public Track getItem(int position) {
        return items.get(position);
    }
}
