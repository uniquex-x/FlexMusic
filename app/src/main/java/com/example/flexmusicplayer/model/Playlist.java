package com.example.flexmusicplayer.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Playlist implements Serializable {
    private long id;
    private String name;
    private String description;
    private String coverUrl;
    private long createdDate;
    private long modifiedDate;
    private List<Song> songs;

    public Playlist() {
        this.createdDate = System.currentTimeMillis();
        this.modifiedDate = System.currentTimeMillis();
        this.songs = new ArrayList<>();
    }

    public Playlist(long id, String name) {
        this();
        this.id = id;
        this.name = name;
    }

    public Playlist(long id, String name, String description) {
        this(id, name);
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public long getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(long modifiedDate) {
        this.modifiedDate = modifiedDate;
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

    public void addSong(Song song) {
        if (songs == null) {
            songs = new ArrayList<>();
        }
        if (!songs.contains(song)) {
            songs.add(song);
            modifiedDate = System.currentTimeMillis();
        }
    }

    public void removeSong(Song song) {
        if (songs != null) {
            songs.remove(song);
            modifiedDate = System.currentTimeMillis();
        }
    }

    public void clearSongs() {
        if (songs != null) {
            songs.clear();
            modifiedDate = System.currentTimeMillis();
        }
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
