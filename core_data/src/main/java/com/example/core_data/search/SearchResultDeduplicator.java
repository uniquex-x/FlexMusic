package com.example.core_data.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;

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
    private String buildKey(@NonNull SearchTrack track) {
        String primaryArtist = track.getArtistNames().isEmpty() ? "" : track.getArtistNames().get(0);
        return normalize(track.getTitle())
                + "|"
                + normalize(primaryArtist)
                + "|"
                + (track.getDurationMs() / 1000L);
    }

    @NonNull
    private String normalize(@NonNull String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
