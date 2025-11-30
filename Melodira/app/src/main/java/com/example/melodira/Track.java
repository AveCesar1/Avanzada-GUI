package com.example.melodira;

public class Track {
    public String title;
    public String artist;
    public String album;
    public String path;
    public long durationMs;

    public Track() {}

    public Track(String title, String artist, String album, String path, long durationMs) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.path = path;
        this.durationMs = durationMs;
    }
}
