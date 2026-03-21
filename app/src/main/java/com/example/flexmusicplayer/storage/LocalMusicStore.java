package com.example.flexmusicplayer.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Song;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalMusicStore {

    private static final String TAG = "LocalMusicStore";
    private static final String PREFS_NAME = "flexmusic_local_library";
    private static final String KEY_SONGS = "songs";

    private final SharedPreferences preferences;
    private final Context appContext;

    public LocalMusicStore(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<Song> loadSongs() {
        List<Song> songs = new ArrayList<>();
        String raw = preferences.getString(KEY_SONGS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                songs.add(fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return songs;
    }

    public void addSongs(@NonNull List<Uri> uris) {
        Map<String, Song> bySource = new LinkedHashMap<>();
        for (Song song : loadSongs()) {
            bySource.put(song.getAudioUrl(), song);
        }
        for (Uri uri : uris) {
            Song song = buildSongFromUri(uri);
            bySource.put(song.getAudioUrl(), song);
        }
        saveSongs(new ArrayList<>(bySource.values()));
    }

    public boolean deleteSong(@NonNull Song targetSong) {
        Uri songUri = parseSongUri(targetSong);
        if (songUri == null) {
            Log.w(TAG, "deleteSong missing uri title=" + targetSong.getTitle());
            return false;
        }
        if (!deleteSongFile(songUri)) {
            Log.w(TAG, "deleteSong file delete failed uri=" + songUri);
            return false;
        }
        boolean removed = removeSongRecord(targetSong);
        Log.d(TAG, "deleteSong success uri=" + songUri + " recordRemoved=" + removed);
        return true;
    }

    private boolean removeSongRecord(@NonNull Song targetSong) {
        List<Song> songs = loadSongs();
        String targetKey = buildSongKey(targetSong);
        boolean removed = false;
        for (int index = songs.size() - 1; index >= 0; index--) {
            Song song = songs.get(index);
            if (!targetKey.equals(buildSongKey(song))) {
                continue;
            }
            songs.remove(index);
            removed = true;
        }
        if (removed) {
            saveSongs(songs);
        }
        return removed;
    }

    private boolean deleteSongFile(@NonNull Uri songUri) {
        String scheme = songUri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            return deleteFileSchemeUri(songUri);
        }
        if ("content".equalsIgnoreCase(scheme)) {
            return deleteContentUri(songUri);
        }
        Log.w(TAG, "deleteSong unsupported scheme uri=" + songUri);
        return false;
    }

    private boolean deleteFileSchemeUri(@NonNull Uri songUri) {
        String path = songUri.getPath();
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File targetFile = new File(path);
        if (!targetFile.exists()) {
            Log.d(TAG, "deleteSong file already missing path=" + path);
            return true;
        }
        boolean deleted = targetFile.delete();
        Log.d(TAG, "deleteSong file path=" + path + " deleted=" + deleted);
        return deleted;
    }

    private boolean deleteContentUri(@NonNull Uri songUri) {
        try {
            boolean deleted = DocumentsContract.deleteDocument(appContext.getContentResolver(), songUri);
            if (deleted) {
                releasePersistableReadPermission(songUri);
            }
            Log.d(TAG, "deleteSong document uri=" + songUri + " deleted=" + deleted);
            return deleted;
        } catch (FileNotFoundException fileNotFoundException) {
            Log.d(TAG, "deleteSong content already missing uri=" + songUri);
            releasePersistableReadPermission(songUri);
            return true;
        } catch (RuntimeException runtimeException) {
            Log.w(TAG, "deleteSong document delete failed uri=" + songUri, runtimeException);
        }

        try {
            int deletedRows = appContext.getContentResolver().delete(songUri, null, null);
            boolean deleted = deletedRows > 0;
            if (deleted) {
                releasePersistableReadPermission(songUri);
            }
            Log.d(TAG, "deleteSong content resolver uri=" + songUri + " deletedRows=" + deletedRows);
            return deleted;
        } catch (RuntimeException exception) {
            Log.w(TAG, "deleteSong content resolver failed uri=" + songUri, exception);
            return false;
        }
    }

    private void releasePersistableReadPermission(@NonNull Uri songUri) {
        try {
            appContext.getContentResolver().releasePersistableUriPermission(
                    songUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private Uri parseSongUri(@NonNull Song song) {
        String audioUrl = song.getAudioUrl();
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(audioUrl);
        } catch (RuntimeException exception) {
            Log.w(TAG, "parseSongUri failed audioUrl=" + audioUrl, exception);
            return null;
        }
    }

    private void saveSongs(@NonNull List<Song> songs) {
        JSONArray array = new JSONArray();
        for (Song song : songs) {
            array.put(toJson(song));
        }
        preferences.edit().putString(KEY_SONGS, array.toString()).apply();
    }

    @NonNull
    private Song buildSongFromUri(@NonNull Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        String title = null;
        String artist = null;
        String album = null;
        int duration = 0;
        try {
            retriever.setDataSource(appContext, uri);
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationString != null) {
                duration = Integer.parseInt(durationString);
            }
        } catch (RuntimeException ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }

        String displayName = queryDisplayName(uri);
        if (title == null || title.trim().isEmpty()) {
            title = stripExtension(displayName);
        }
        if (artist == null || artist.trim().isEmpty()) {
            artist = appContext.getString(R.string.player_unknown_artist);
        }
        if (album == null || album.trim().isEmpty()) {
            album = appContext.getString(R.string.player_unknown_album);
        }

        Song song = new Song(Math.abs(uri.toString().hashCode()), title, artist, album, duration, uri.toString());
        song.setLocal(true);
        return song;
    }

    private String queryDisplayName(@NonNull Uri uri) {
        try (android.database.Cursor cursor = appContext.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
        }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : appContext.getString(R.string.transcode_unknown_file);
    }

    private String stripExtension(String name) {
        if (name == null) {
            return appContext.getString(R.string.transcode_unknown_file);
        }
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    private JSONObject toJson(@NonNull Song song) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", song.getId());
            object.put("title", song.getTitle());
            object.put("artist", song.getArtist());
            object.put("album", song.getAlbum());
            object.put("duration", song.getDuration());
            object.put("audioUrl", song.getAudioUrl());
            object.put("isLocal", song.isLocal());
        } catch (JSONException ignored) {
        }
        return object;
    }

    private Song fromJson(@NonNull JSONObject object) throws JSONException {
        Song song = new Song(
                object.getLong("id"),
                object.optString("title"),
                object.optString("artist"),
                object.optString("album"),
                object.optInt("duration"),
                object.optString("audioUrl"));
        song.setLocal(object.optBoolean("isLocal", true));
        return song;
    }

    @NonNull
    private String buildSongKey(@NonNull Song song) {
        String audioUrl = song.getAudioUrl();
        if (audioUrl != null && !audioUrl.trim().isEmpty()) {
            return audioUrl;
        }
        String sourceId = song.getSourceId();
        if (sourceId != null && !sourceId.trim().isEmpty()) {
            return sourceId;
        }
        return song.getTitle() + "|" + song.getArtist() + "|" + song.getAlbum();
    }
}
