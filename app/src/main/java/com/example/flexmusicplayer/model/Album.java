package com.example.flexmusicplayer.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Album implements Serializable {
    private long id;
    private String name;
    private String artist;
    private int year;
    private String coverUrl;
    private List<Song> songs;

    public Album() {
        this.songs = new ArrayList<>();
    }

    public Album(long id, String name, String artist) {
        this();
        this.id = id;
        this.name = name;
        this.artist = artist;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public int getSongCount() {
        return songs != null ? songs.size() : 0;
    }

    public int getTotalDuration() {
        int total = 0;
        if (songs != null) {
            for (Song song : songs) {
                total += song.getDuration();
            }
        }
        return total;
    }

    public String getFormattedTotalDuration() {
        int total = getTotalDuration();
        int seconds = (total / 1000) % 60;
        int minutes = (total / (1000 * 60)) % 60;
        int hours = (total / (1000 * 60 * 60));

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
