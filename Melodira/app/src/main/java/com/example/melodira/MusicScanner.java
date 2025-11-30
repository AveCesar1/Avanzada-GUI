package com.example.melodira;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

public class MusicScanner {
    private static final String TAG = "MusicScanner";
    private static final String PREFS = "melodira_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    /**
     * Main entry: intenta MediaStore; si no encuentra nada intenta escanear un DocumentFile tree
     * previamente guardado en SharedPreferences (SAF). Devuelve lista (posible vacía).
     */
    public static List<Track> scanDownloadsAndDocuments(Context context) {
        List<Track> list = new ArrayList<>();

        String[] projection = {
                MediaStore.Audio.Media.DATA,     // 0. LA RUTA REAL
                MediaStore.Audio.Media.TITLE,    // 1. EL TÍTULO
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int colData = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int colTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int colArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int colAlbum = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int colDur = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

                do {
                    // Obtenemos los datos de la base de datos
                    String realPath = cursor.getString(colData);   // RUTA (/storage/...)
                    String realTitle = cursor.getString(colTitle); // TÍTULO (Nombre canción)
                    String artist = (colArtist != -1) ? cursor.getString(colArtist) : "Desconocido";
                    String album = (colAlbum != -1) ? cursor.getString(colAlbum) : "Desconocido";
                    long duration = (colDur != -1) ? cursor.getLong(colDur) : 0;

                    // Filtro de seguridad
                    if (realPath != null && realPath.endsWith(".mp3")) {

                        // CORRECCIÓN: Aseguramos el orden correcto:
                        // 1. path, 2. title, 3. artist, 4. album, 5. duration
                        Track t = new Track(realPath, realTitle, artist, album, duration);

                        list.add(t);
                    }

                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Escanea MediaStore buscando archivos .mp3 (o mime audio/mpeg). Devuelve lista de Track.
     */
    public static List<Track> scanMediaStoreForMp3(Context ctx) {
        List<Track> out = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();

        // Intentamos seleccionar por MIME y por extensión (ambos para maximizar compatibilidad)
        String[] projection = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,       // puede estar deprecated en algunos Sdk, pero útil aquí
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        Cursor c = null;
        try {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            c = cr.query(uri, projection, selection, null, null);
            if (c != null) {
                int idxTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int idxArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int idxAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int idxData = c.getColumnIndex(MediaStore.Audio.Media.DATA);
                int idxDuration = c.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int idxMime = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

                while (c.moveToNext()) {
                    String mime = idxMime >= 0 ? c.getString(idxMime) : null;
                    String data = idxData >= 0 ? c.getString(idxData) : null;

                    boolean isMp3 = false;
                    if (!TextUtils.isEmpty(mime) && mime.equalsIgnoreCase("audio/mpeg")) isMp3 = true;
                    if (!isMp3 && !TextUtils.isEmpty(data) && data.toLowerCase().endsWith(".mp3")) isMp3 = true;
                    if (!isMp3) continue;

                    String title = idxTitle >= 0 ? c.getString(idxTitle) : null;
                    String artist = idxArtist >= 0 ? c.getString(idxArtist) : "";
                    String album = idxAlbum >= 0 ? c.getString(idxAlbum) : "";
                    long dur = idxDuration >= 0 ? (c.isNull(idxDuration) ? 0 : c.getLong(idxDuration)) : 0L;

                    if (TextUtils.isEmpty(title) && data != null) {
                        int slash = data.lastIndexOf('/');
                        title = (slash >= 0) ? data.substring(slash + 1) : data;
                    }
                    Track t = new Track(title == null ? "Unknown" : title, artist, album, data, dur);
                    out.add(t);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore query error: " + e.getMessage());
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /**
     * Escanea un treeUri (DocumentFile) recursivamente y devuelve todos los mp3 encontrados.
     * Debes asegurarte de haber tomado persistable permissions antes de llamar a esto.
     */

    /*
    public static List<Track> scanDocumentTree(Context ctx, Uri treeUri) {
        List<Track> out = new ArrayList<>();
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(ctx, treeUri);
            if (tree == null || !tree.exists()) return out;
            traverseDocumentFile(tree, out);
        } catch (Exception e) {
            Log.w(TAG, "scanDocumentTree error: " + e.getMessage());
        }
        return out;
    }
    */

    public static List<Track> scanDocumentTree(Context context, Uri treeUri) {
        List<Track> tracks = new ArrayList<>();
        DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);

        if (dir != null && dir.isDirectory()) {
            for (DocumentFile file : dir.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().toLowerCase().endsWith(".mp3")) {

                    // 1. Preparamos las variables
                    String path = file.getUri().toString();
                    String title = file.getName().replace(".mp3", "");
                    String artist = "Desconocido";
                    String album = "Desconocido";
                    long duration = 0;

                    // 2. Intentamos leer metadatos reales
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(context, file.getUri());

                        String metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        String metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        String metaAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        String metaDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                        if (metaTitle != null && !metaTitle.isEmpty()) title = metaTitle;
                        if (metaArtist != null && !metaArtist.isEmpty()) artist = metaArtist;
                        if (metaAlbum != null) album = metaAlbum;
                        if (metaDuration != null) duration = Long.parseLong(metaDuration);

                    } catch (Exception e) {
                        // Si falla, se quedan los valores por defecto definidos arriba
                    } finally {
                        try { mmr.release(); } catch (Exception ignored) {}
                    }

                    // 3. AHORA SÍ creamos el Track con todos los datos juntos
                    // CORRECCIÓN: Usamos el constructor con argumentos
                    Track t = new Track(path, title, artist, album, duration);
                    tracks.add(t);
                }
            }
        }
        return tracks;
    }

    private static void traverseDocumentFile(DocumentFile node, List<Track> out) {
        if (node == null || !node.exists()) return;
        if (node.isDirectory()) {
            for (DocumentFile child : node.listFiles()) {
                traverseDocumentFile(child, out);
            }
        } else if (node.isFile()) {
            String name = node.getName() != null ? node.getName() : "";
            if (name.toLowerCase().endsWith(".mp3")) {

                String path = node.getUri().toString();
                String title = name.replace(".mp3", ""); // Quitamos la extensión para que se vea mejor

                // CORRECCIÓN: Creamos el Track con datos directos
                // Como este método es simple, no calculamos duración ni artista
                Track t = new Track(path, title, "Desconocido", "Desconocido", 0);

                out.add(t);
            }
        }
    }

    // Guarda el tree uri en SharedPreferences para usarlo después.
    public static void saveTreeUri(Context ctx, Uri treeUri) {
        if (treeUri == null) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString(KEY_TREE_URI, treeUri.toString()).apply();
    }

    public static String getSavedTreeUri(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString(KEY_TREE_URI, null);
    }

    // Borra el treeUri guardado (por si el usuario revoca permisos)
    public static void clearSavedTreeUri(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().remove(KEY_TREE_URI).apply();
    }
}
