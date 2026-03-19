package com.example.core_data.search;

import androidx.annotation.NonNull;

import com.example.core_domain.search.SearchAvailability;
import com.example.core_domain.search.SearchAlbum;
import com.example.core_domain.search.SearchArtist;
import com.example.core_domain.search.SearchPlaylist;
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

    @NonNull
    public List<SearchAlbum> rankAlbums(@NonNull List<SearchAlbum> albums, @NonNull String keyword) {
        List<SearchAlbum> rankedAlbums = new ArrayList<>(albums);
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        Collections.sort(rankedAlbums, Comparator.comparingInt(album -> -score(album, normalizedKeyword)));
        return rankedAlbums;
    }

    @NonNull
    public List<SearchArtist> rankArtists(@NonNull List<SearchArtist> artists, @NonNull String keyword) {
        List<SearchArtist> rankedArtists = new ArrayList<>(artists);
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        Collections.sort(rankedArtists, Comparator.comparingInt(artist -> -score(artist, normalizedKeyword)));
        return rankedArtists;
    }

    @NonNull
    public List<SearchPlaylist> rankPlaylists(@NonNull List<SearchPlaylist> playlists, @NonNull String keyword) {
        List<SearchPlaylist> rankedPlaylists = new ArrayList<>(playlists);
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        Collections.sort(rankedPlaylists, Comparator.comparingInt(playlist -> -score(playlist, normalizedKeyword)));
        return rankedPlaylists;
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

    private int score(@NonNull SearchAlbum album, @NonNull String keyword) {
        if (keyword.isEmpty()) {
            return album.getTrackCount();
        }
        int score = 0;
        String title = album.getTitle().toLowerCase(Locale.ROOT);
        String primaryArtist = album.getArtistNames().isEmpty()
                ? ""
                : album.getArtistNames().get(0).toLowerCase(Locale.ROOT);
        if (title.equals(keyword)) {
            score += 120;
        } else if (title.contains(keyword)) {
            score += 80;
        }
        if (primaryArtist.contains(keyword)) {
            score += 24;
        }
        score += Math.min(20, album.getTrackCount());
        return score;
    }

    private int score(@NonNull SearchArtist artist, @NonNull String keyword) {
        if (keyword.isEmpty()) {
            return 1;
        }
        String name = artist.getName().toLowerCase(Locale.ROOT);
        if (name.equals(keyword)) {
            return 120;
        }
        if (name.startsWith(keyword)) {
            return 90;
        }
        if (name.contains(keyword)) {
            return 70;
        }
        return 1;
    }

    private int score(@NonNull SearchPlaylist playlist, @NonNull String keyword) {
        if (keyword.isEmpty()) {
            return playlist.getTrackCount();
        }
        int score = 0;
        String title = playlist.getTitle().toLowerCase(Locale.ROOT);
        String creator = playlist.getCreatorName().toLowerCase(Locale.ROOT);
        if (title.equals(keyword)) {
            score += 120;
        } else if (title.contains(keyword)) {
            score += 80;
        }
        if (creator.contains(keyword)) {
            score += 20;
        }
        score += Math.min(20, playlist.getTrackCount());
        return score;
    }
}
