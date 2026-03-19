package com.example.flexmusicplayer.storage;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.flexmusicplayer.model.Song;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RecentPlaybackStore {

    public interface Listener {
        void onRecentSongsChanged(@NonNull List<Song> songs);
    }

    private static final int MAX_RECENT_SONGS = 300;
    private static final RecentPlaybackStore INSTANCE = new RecentPlaybackStore();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Deque<Song> recentSongs = new ArrayDeque<>();
    private final Set<Listener> listeners = new LinkedHashSet<>();

    private RecentPlaybackStore() {
    }

    @NonNull
    public static RecentPlaybackStore getInstance() {
        return INSTANCE;
    }

    public synchronized void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        notifyListener(listener, getRecentSongs());
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public synchronized List<Song> getRecentSongs() {
        return new ArrayList<>(recentSongs);
    }

    public synchronized void recordPlayback(@NonNull Song song) {
        if (song.isRadioStream()) {
            return;
        }
        Song historyItem = copySong(song);
        historyItem.setLastPlayedDate(System.currentTimeMillis());
        removeExistingLocked(historyItem);
        recentSongs.addFirst(historyItem);
        while (recentSongs.size() > MAX_RECENT_SONGS) {
            recentSongs.removeLast();
        }
        dispatch();
    }

    private void dispatch() {
        List<Song> snapshot = getRecentSongs();
        for (Listener listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(@NonNull Listener listener, @NonNull List<Song> songs) {
        mainHandler.post(() -> listener.onRecentSongsChanged(new ArrayList<>(songs)));
    }

    private void removeExistingLocked(@NonNull Song song) {
        Iterator<Song> iterator = recentSongs.iterator();
        while (iterator.hasNext()) {
            if (sameSong(iterator.next(), song)) {
                iterator.remove();
            }
        }
    }

    private boolean sameSong(@NonNull Song left, @NonNull Song right) {
        return buildPlaybackKey(left).equals(buildPlaybackKey(right));
    }

    @NonNull
    private String buildPlaybackKey(@NonNull Song song) {
        String sourceId = song.getSourceId();
        if (sourceId == null || sourceId.isEmpty()) {
            sourceId = String.valueOf(song.getId());
        }
        return sourceId + "|" + String.valueOf(song.getAudioUrl());
    }

    @NonNull
    private Song copySong(@NonNull Song source) {
        Song song = new Song(source.getId(), source.getTitle(), source.getArtist(), source.getAlbum(), source.getDuration(), source.getAudioUrl());
        song.setAlbumArtUrl(source.getAlbumArtUrl());
        song.setLocal(source.isLocal());
        song.setFavorite(source.isFavorite());
        song.setDownloaded(source.isDownloaded());
        song.setAddedDate(source.getAddedDate());
        song.setLastPlayedDate(source.getLastPlayedDate());
        song.setRadioStream(source.isRadioStream());
        song.setSourceId(source.getSourceId());
        return song;
    }
}
