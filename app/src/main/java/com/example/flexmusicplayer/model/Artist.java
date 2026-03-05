package com.example.flexmusicplayer.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Artist implements Serializable {
    private long id;
    private String name;
    private String imageUrl;
    private String bio;
    private List<Song> songs;
    private List<Album> albums;

    public Artist() {
        this.songs = new ArrayList<>();
        this.albums = new ArrayList<>();
    }

    public Artist(long id, String name) {
        this();
        this.id = id;
        this.name = name;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    public int getSongCount() {
        return songs != null ? songs.size() : 0;
    }

    public int getAlbumCount() {
        return albums != null ? albums.size() : 0;
    }
}
