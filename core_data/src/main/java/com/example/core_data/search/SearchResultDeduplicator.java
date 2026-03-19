package com.example.core_data.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_domain.search.SearchAlbum;
import com.example.core_domain.search.SearchArtist;
import com.example.core_domain.search.SearchPlaylist;
import com.example.core_domain.search.SearchTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SearchResultDeduplicator {

    @NonNull
    public List<SearchTrack> deduplicateTracks(@NonNull List<SearchTrack> tracks) {
        Map<String, SearchTrack> deduplicated = new LinkedHashMap<>();
        for (SearchTrack track : tracks) {
            String key = buildKey(track);
            SearchTrack existing = deduplicated.get(key);
            if (existing == null || track.getPlaybackIntent().isRequiresResolve() == existing.getPlaybackIntent().isRequiresResolve()) {
                deduplicated.put(key, track);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    @NonNull
    public List<SearchAlbum> deduplicateAlbums(@NonNull List<SearchAlbum> albums) {
        Map<String, SearchAlbum> deduplicated = new LinkedHashMap<>();
        for (SearchAlbum album : albums) {
            deduplicated.put(buildKey(album), album);
        }
        return new ArrayList<>(deduplicated.values());
    }

    @NonNull
    public List<SearchArtist> deduplicateArtists(@NonNull List<SearchArtist> artists) {
        Map<String, SearchArtist> deduplicated = new LinkedHashMap<>();
        for (SearchArtist artist : artists) {
            deduplicated.put(buildKey(artist), artist);
        }
        return new ArrayList<>(deduplicated.values());
    }

    @NonNull
    public List<SearchPlaylist> deduplicatePlaylists(@NonNull List<SearchPlaylist> playlists) {
        Map<String, SearchPlaylist> deduplicated = new LinkedHashMap<>();
        for (SearchPlaylist playlist : playlists) {
            deduplicated.put(buildKey(playlist), playlist);
        }
        return new ArrayList<>(deduplicated.values());
    }

    @NonNull
    private String buildKey(@NonNull SearchTrack track) {
        String primaryArtist = track.getArtistNames().isEmpty() ? "" : track.getArtistNames().get(0);
        return normalize(track.getTitle())
                + "|"
                + normalize(primaryArtist)
                + "|"
                + (track.getDurationMs() / 1000L);
    }

    @NonNull
    private String buildKey(@NonNull SearchAlbum album) {
        String primaryArtist = album.getArtistNames().isEmpty() ? "" : album.getArtistNames().get(0);
        return normalize(album.getTitle()) + "|" + normalize(primaryArtist);
    }

    @NonNull
    private String buildKey(@NonNull SearchArtist artist) {
        return normalize(artist.getName());
    }

    @NonNull
    private String buildKey(@NonNull SearchPlaylist playlist) {
        return normalize(playlist.getTitle()) + "|" + normalize(playlist.getCreatorName());
    }

    @NonNull
    private String normalize(@NonNull String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
