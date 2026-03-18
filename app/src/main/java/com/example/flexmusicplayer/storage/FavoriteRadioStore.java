package com.example.flexmusicplayer.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.flexmusicplayer.model.Song;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FavoriteRadioStore {

    private static final String PREFS_NAME = "flexmusic_favorite_radios";
    private static final String KEY_FAVORITES = "favorite_radios";
    private static final int MAX_FAVORITES = 100;

    private final SharedPreferences preferences;

    public FavoriteRadioStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<Song> loadFavorites() {
        List<Song> favorites = new ArrayList<>();
        String raw = preferences.getString(KEY_FAVORITES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Song radio = fromJson(array.getJSONObject(i));
                radio.setFavorite(true);
                favorites.add(radio);
            }
        } catch (JSONException ignored) {
        }
        return favorites;
    }

    public boolean isFavorite(@NonNull Song song) {
        String key = buildRadioKey(song);
        for (Song favorite : loadFavorites()) {
            if (key.equals(buildRadioKey(favorite))) {
                return true;
            }
        }
        return false;
    }

    public boolean toggleFavorite(@NonNull Song song) {
        List<Song> favorites = loadFavorites();
        String key = buildRadioKey(song);
        Iterator<Song> iterator = favorites.iterator();
        while (iterator.hasNext()) {
            if (key.equals(buildRadioKey(iterator.next()))) {
                iterator.remove();
                saveFavorites(favorites);
                song.setFavorite(false);
                return false;
            }
        }
        Song favoriteCopy = copySong(song);
        favoriteCopy.setFavorite(true);
        favoriteCopy.setRadioStream(true);
        favorites.add(0, favoriteCopy);
        while (favorites.size() > MAX_FAVORITES) {
            favorites.remove(favorites.size() - 1);
        }
        saveFavorites(favorites);
        song.setFavorite(true);
        return true;
    }

    private void saveFavorites(@NonNull List<Song> songs) {
        JSONArray array = new JSONArray();
        for (Song song : songs) {
            array.put(toJson(song));
        }
        preferences.edit().putString(KEY_FAVORITES, array.toString()).apply();
    }

    @NonNull
    private String buildRadioKey(@NonNull Song song) {
        if (song.getSourceId() != null && !song.getSourceId().trim().isEmpty()) {
            return song.getSourceId();
        }
        return song.getAudioUrl() != null ? song.getAudioUrl() : song.getTitle();
    }

    @NonNull
    private Song copySong(@NonNull Song source) {
        Song song = new Song(source.getId(), source.getTitle(), source.getArtist(), source.getAlbum(), source.getDuration(), source.getAudioUrl());
        song.setAlbumArtUrl(source.getAlbumArtUrl());
        song.setLocal(source.isLocal());
        song.setDownloaded(source.isDownloaded());
        song.setAddedDate(source.getAddedDate());
        song.setLastPlayedDate(source.getLastPlayedDate());
        song.setRadioStream(source.isRadioStream());
        song.setSourceId(source.getSourceId());
        return song;
    }

    @NonNull
    private JSONObject toJson(@NonNull Song song) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", song.getId());
            object.put("title", song.getTitle());
            object.put("artist", song.getArtist());
            object.put("album", song.getAlbum());
            object.put("duration", song.getDuration());
            object.put("audioUrl", song.getAudioUrl());
            object.put("albumArtUrl", song.getAlbumArtUrl());
            object.put("isLocal", song.isLocal());
            object.put("isDownloaded", song.isDownloaded());
            object.put("addedDate", song.getAddedDate());
            object.put("lastPlayedDate", song.getLastPlayedDate());
            object.put("isRadioStream", song.isRadioStream());
            object.put("sourceId", song.getSourceId());
        } catch (JSONException ignored) {
        }
        return object;
    }

    @NonNull
    private Song fromJson(@NonNull JSONObject object) {
        Song song = new Song(
                object.optLong("id"),
                object.optString("title"),
                object.optString("artist"),
                object.optString("album"),
                object.optInt("duration"),
                object.optString("audioUrl"));
        song.setAlbumArtUrl(object.optString("albumArtUrl"));
        song.setLocal(object.optBoolean("isLocal"));
        song.setDownloaded(object.optBoolean("isDownloaded"));
        song.setAddedDate(object.optLong("addedDate"));
        song.setLastPlayedDate(object.optLong("lastPlayedDate"));
        song.setRadioStream(object.optBoolean("isRadioStream"));
        song.setSourceId(object.optString("sourceId"));
        song.setFavorite(true);
        return song;
    }
}
