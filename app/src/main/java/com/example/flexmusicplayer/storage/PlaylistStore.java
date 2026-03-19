package com.example.flexmusicplayer.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.flexmusicplayer.model.Playlist;
import com.example.flexmusicplayer.model.Song;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class PlaylistStore {

    public enum AddSongResult {
        ADDED,
        ALREADY_EXISTS,
        PLAYLIST_MISSING
    }

    private static final String PREFS_NAME = "flexmusic_playlists";
    private static final String KEY_PLAYLISTS = "playlists";

    private final SharedPreferences preferences;

    public PlaylistStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<Playlist> loadPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        String raw = preferences.getString(KEY_PLAYLISTS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                playlists.add(fromPlaylistJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
        }
        Collections.sort(playlists, Comparator.comparingLong(Playlist::getModifiedDate).reversed());
        return playlists;
    }

    @NonNull
    public Playlist createPlaylist(@NonNull String name, @NonNull String description) {
        Playlist playlist = new Playlist(System.currentTimeMillis(), name.trim(), description.trim());
        List<Playlist> playlists = loadPlaylists();
        playlists.add(0, playlist);
        savePlaylists(playlists);
        return playlist;
    }

    @NonNull
    public Playlist createPlaylistWithSong(@NonNull String name,
                                           @NonNull String description,
                                           @NonNull Song song) {
        Playlist playlist = createPlaylist(name, description);
        addSongToPlaylist(playlist.getId(), song);
        return playlist;
    }

    public boolean updatePlaylist(@NonNull Playlist updatedPlaylist) {
        List<Playlist> playlists = loadPlaylists();
        for (int index = 0; index < playlists.size(); index++) {
            Playlist playlist = playlists.get(index);
            if (playlist.getId() == updatedPlaylist.getId()) {
                updatedPlaylist.setModifiedDate(System.currentTimeMillis());
                playlists.set(index, copyPlaylist(updatedPlaylist));
                savePlaylists(playlists);
                return true;
            }
        }
        return false;
    }

    public boolean deletePlaylist(long playlistId) {
        List<Playlist> playlists = loadPlaylists();
        Iterator<Playlist> iterator = playlists.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getId() == playlistId) {
                iterator.remove();
                savePlaylists(playlists);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Playlist findPlaylistById(long playlistId) {
        for (Playlist playlist : loadPlaylists()) {
            if (playlist.getId() == playlistId) {
                return playlist;
            }
        }
        return null;
    }

    @NonNull
    public AddSongResult addSongToPlaylist(long playlistId, @NonNull Song song) {
        List<Playlist> playlists = loadPlaylists();
        for (Playlist playlist : playlists) {
            if (playlist.getId() != playlistId) {
                continue;
            }
            if (containsSong(playlist, song)) {
                return AddSongResult.ALREADY_EXISTS;
            }
            playlist.addSong(copySong(song));
            if (TextUtils.isEmpty(playlist.getCoverUrl()) && !TextUtils.isEmpty(song.getAlbumArtUrl())) {
                playlist.setCoverUrl(song.getAlbumArtUrl());
            }
            playlist.setModifiedDate(System.currentTimeMillis());
            savePlaylists(playlists);
            return AddSongResult.ADDED;
        }
        return AddSongResult.PLAYLIST_MISSING;
    }

    public boolean removeSongFromPlaylist(long playlistId, @NonNull Song song) {
        List<Playlist> playlists = loadPlaylists();
        for (Playlist playlist : playlists) {
            if (playlist.getId() != playlistId) {
                continue;
            }
            List<Song> songs = playlist.getSongs();
            if (songs == null || songs.isEmpty()) {
                return false;
            }
            String targetKey = buildSongKey(song);
            Iterator<Song> iterator = songs.iterator();
            while (iterator.hasNext()) {
                if (targetKey.equals(buildSongKey(iterator.next()))) {
                    iterator.remove();
                    playlist.setSongs(songs);
                    playlist.setModifiedDate(System.currentTimeMillis());
                    savePlaylists(playlists);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean containsSong(@NonNull Playlist playlist, @NonNull Song targetSong) {
        String targetKey = buildSongKey(targetSong);
        List<Song> songs = playlist.getSongs();
        if (songs == null) {
            return false;
        }
        for (Song song : songs) {
            if (targetKey.equals(buildSongKey(song))) {
                return true;
            }
        }
        return false;
    }

    private void savePlaylists(@NonNull List<Playlist> playlists) {
        JSONArray array = new JSONArray();
        for (Playlist playlist : playlists) {
            array.put(toPlaylistJson(playlist));
        }
        preferences.edit().putString(KEY_PLAYLISTS, array.toString()).apply();
    }

    @NonNull
    private Playlist copyPlaylist(@NonNull Playlist source) {
        Playlist playlist = new Playlist(source.getId(), source.getName(), source.getDescription());
        playlist.setCreatedDate(source.getCreatedDate());
        playlist.setModifiedDate(source.getModifiedDate());
        playlist.setCoverUrl(source.getCoverUrl());
        List<Song> songs = new ArrayList<>();
        if (source.getSongs() != null) {
            for (Song song : source.getSongs()) {
                songs.add(copySong(song));
            }
        }
        playlist.setSongs(songs);
        return playlist;
    }

    @NonNull
    private Song copySong(@NonNull Song source) {
        Song song = new Song(
                source.getId(),
                source.getTitle(),
                source.getArtist(),
                source.getAlbum(),
                source.getDuration(),
                source.getAudioUrl());
        song.setAlbumArtUrl(source.getAlbumArtUrl());
        song.setLocal(source.isLocal());
        song.setFavorite(source.isFavorite());
        song.setDownloaded(source.isDownloaded());
        song.setRadioStream(source.isRadioStream());
        song.setAddedDate(source.getAddedDate());
        song.setLastPlayedDate(source.getLastPlayedDate());
        song.setSourceId(source.getSourceId());
        return song;
    }

    @NonNull
    private JSONObject toPlaylistJson(@NonNull Playlist playlist) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", playlist.getId());
            object.put("name", playlist.getName());
            object.put("description", playlist.getDescription());
            object.put("coverUrl", playlist.getCoverUrl());
            object.put("createdDate", playlist.getCreatedDate());
            object.put("modifiedDate", playlist.getModifiedDate());
            JSONArray songsArray = new JSONArray();
            if (playlist.getSongs() != null) {
                for (Song song : playlist.getSongs()) {
                    songsArray.put(toSongJson(song));
                }
            }
            object.put("songs", songsArray);
        } catch (JSONException ignored) {
        }
        return object;
    }

    @NonNull
    private Playlist fromPlaylistJson(@NonNull JSONObject object) throws JSONException {
        Playlist playlist = new Playlist(
                object.optLong("id"),
                object.optString("name"),
                object.optString("description"));
        playlist.setCoverUrl(object.optString("coverUrl"));
        playlist.setCreatedDate(object.optLong("createdDate", System.currentTimeMillis()));
        playlist.setModifiedDate(object.optLong("modifiedDate", playlist.getCreatedDate()));
        JSONArray songsArray = object.optJSONArray("songs");
        List<Song> songs = new ArrayList<>();
        if (songsArray != null) {
            for (int index = 0; index < songsArray.length(); index++) {
                songs.add(fromSongJson(songsArray.getJSONObject(index)));
            }
        }
        playlist.setSongs(songs);
        return playlist;
    }

    @NonNull
    private JSONObject toSongJson(@NonNull Song song) {
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
            object.put("isFavorite", song.isFavorite());
            object.put("isDownloaded", song.isDownloaded());
            object.put("isRadioStream", song.isRadioStream());
            object.put("addedDate", song.getAddedDate());
            object.put("lastPlayedDate", song.getLastPlayedDate());
            object.put("sourceId", song.getSourceId());
        } catch (JSONException ignored) {
        }
        return object;
    }

    @NonNull
    private Song fromSongJson(@NonNull JSONObject object) {
        Song song = new Song(
                object.optLong("id"),
                object.optString("title"),
                object.optString("artist"),
                object.optString("album"),
                object.optInt("duration"),
                object.optString("audioUrl"));
        song.setAlbumArtUrl(object.optString("albumArtUrl"));
        song.setLocal(object.optBoolean("isLocal"));
        song.setFavorite(object.optBoolean("isFavorite"));
        song.setDownloaded(object.optBoolean("isDownloaded"));
        song.setRadioStream(object.optBoolean("isRadioStream"));
        song.setAddedDate(object.optLong("addedDate"));
        song.setLastPlayedDate(object.optLong("lastPlayedDate"));
        song.setSourceId(object.optString("sourceId"));
        return song;
    }

    @NonNull
    private String buildSongKey(@NonNull Song song) {
        if (!TextUtils.isEmpty(song.getSourceId())) {
            return song.getSourceId();
        }
        if (!TextUtils.isEmpty(song.getAudioUrl())) {
            return song.getAudioUrl();
        }
        return String.valueOf(song.getTitle()) + "|" + song.getArtist() + "|" + song.getAlbum();
    }
}
