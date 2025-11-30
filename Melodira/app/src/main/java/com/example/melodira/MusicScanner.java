package com.example.melodira;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
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
    public static List<Track> scanDownloadsAndDocuments(Context ctx) {
        List<Track> result = new ArrayList<>();

        // 1) Intento MediaStore (rápido + moderno)
        try {
            result = scanMediaStoreForMp3(ctx);
        } catch (Exception e) {
            Log.w(TAG, "MediaStore scan failed: " + e.getMessage());
            result = new ArrayList<>();
        }

        // 2) Si MediaStore no arrojó nada, intenta SAF usando treeUri guardado
        if (result.isEmpty()) {
            String tree = getSavedTreeUri(ctx);
            if (!TextUtils.isEmpty(tree)) {
                try {
                    Uri treeUri = Uri.parse(tree);
                    List<Track> safTracks = scanDocumentTree(ctx, treeUri);
                    if (!safTracks.isEmpty()) {
                        result = safTracks;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "SAF scan failed: " + e.getMessage());
                }
            }
        }

        return result;
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

        // 1. Usar DocumentFile para manejar el Uri del árbol
        DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);

        if (dir != null && dir.isDirectory()) {
            // 2. Iterar sobre los archivos
            for (DocumentFile file : dir.listFiles()) {
                if (file.isFile()) {
                    String name = file.getName();
                    // 3. Filtro simple por extensión
                    if (name != null && name.toLowerCase().endsWith(".mp3")) {

                        // IMPORTANTE: Crear el Track usando el URI del archivo, no un path
                        Track t = new Track();
                        t.title = name.replace(".mp3", ""); // Nombre temporal
                        t.artist = "Desconocido";
                        t.path = file.getUri().toString(); // <--- CLAVE: Guardar URI como String
                        t.durationMs = 0; // Se actualizará al reproducir si tu servicio lo maneja

                        tracks.add(t);
                    }
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
                // metadata mínima: no tenemos duración sin abrir el file, dejamos ruta uri
                Track t = new Track();
                t.title = name;
                t.artist = "";
                t.album = "";
                t.path = node.getUri().toString(); // nota: path aquí es URI SAF, procesa con ContentResolver si quieres reproducir
                t.durationMs = 0;
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
