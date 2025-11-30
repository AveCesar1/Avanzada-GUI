package com.example.melodira;

public class Track {
    public String path;     // Ruta del archivo (Lo que se reproduce)
    public String title;    // Título (Lo que se ve)
    public String artist;
    public String album;
    public long durationMs;

    // IMPORTANTE: El orden aquí debe coincidir con el orden en MusicScanner
    public Track(String path, String title, String artist, String album, long durationMs) {
        this.path = path;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
    }
}
