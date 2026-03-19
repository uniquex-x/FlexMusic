package com.example.core_data.search;

import androidx.annotation.NonNull;

import com.example.core_domain.search.SearchAvailability;
import com.example.core_domain.search.SearchTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SearchResultRanker {

    @NonNull
    public List<SearchTrack> rankTracks(@NonNull List<SearchTrack> tracks, @NonNull String keyword) {
        List<SearchTrack> rankedTracks = new ArrayList<>(tracks);
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        Collections.sort(rankedTracks, Comparator.comparingInt(track -> -score(track, normalizedKeyword)));
        return rankedTracks;
    }

    private int score(@NonNull SearchTrack track, @NonNull String keyword) {
        if (keyword.isEmpty()) {
            return track.getAvailability() == SearchAvailability.UNAVAILABLE ? 0 : 1;
        }
        int score = 0;
        String title = track.getTitle().toLowerCase(Locale.ROOT);
        String subtitle = track.getSubtitle().toLowerCase(Locale.ROOT);
        String albumName = track.getAlbumName().toLowerCase(Locale.ROOT);
        if (title.equals(keyword)) {
            score += 120;
        } else if (title.contains(keyword)) {
            score += 80;
        }
        if (subtitle.contains(keyword)) {
            score += 20;
        }
        if (albumName.contains(keyword)) {
            score += 15;
        }
        if (track.getAvailability() == SearchAvailability.AVAILABLE
                || track.getAvailability() == SearchAvailability.REQUIRES_RESOLVE) {
            score += 10;
        }
        if (!track.getQualitySummary().isEmpty()) {
            score += 5;
        }
        return score;
    }
}
