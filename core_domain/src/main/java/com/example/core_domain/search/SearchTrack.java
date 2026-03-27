package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchTrack {

    private final String trackId;
    private final String providerId;
    private final String title;
    private final String subtitle;
    private final List<String> artistNames;
    private final String albumName;
    private final long durationMs;
    private final String coverUrl;
    private final SearchAvailability availability;
    private final String qualitySummary;
    private final TrackPlaybackIntent playbackIntent;
    private final boolean previewPlayback;
    private final String playbackNotice;

    public SearchTrack(@NonNull String trackId,
                       @NonNull String providerId,
                       @NonNull String title,
                       @NonNull String subtitle,
                       @NonNull List<String> artistNames,
                       @NonNull String albumName,
                       long durationMs,
                       @NonNull String coverUrl,
                       @NonNull SearchAvailability availability,
                       @NonNull String qualitySummary,
                       @NonNull TrackPlaybackIntent playbackIntent,
                       boolean previewPlayback,
                       @NonNull String playbackNotice) {
        this.trackId = trackId;
        this.providerId = providerId;
        this.title = title;
        this.subtitle = subtitle;
        this.artistNames = Collections.unmodifiableList(new ArrayList<>(artistNames));
        this.albumName = albumName;
        this.durationMs = Math.max(0L, durationMs);
        this.coverUrl = coverUrl;
        this.availability = availability;
        this.qualitySummary = qualitySummary;
        this.playbackIntent = playbackIntent;
        this.previewPlayback = previewPlayback;
        this.playbackNotice = playbackNotice;
    }

    @NonNull
    public String getTrackId() {
        return trackId;
    }

    @NonNull
    public String getProviderId() {
        return providerId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getSubtitle() {
        return subtitle;
    }

    @NonNull
    public List<String> getArtistNames() {
        return artistNames;
    }

    @NonNull
    public String getAlbumName() {
        return albumName;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @NonNull
    public String getCoverUrl() {
        return coverUrl;
    }

    @NonNull
    public SearchAvailability getAvailability() {
        return availability;
    }

    @NonNull
    public String getQualitySummary() {
        return qualitySummary;
    }

    @NonNull
    public TrackPlaybackIntent getPlaybackIntent() {
        return playbackIntent;
    }

    public boolean isPreviewPlayback() {
        return previewPlayback;
    }

    @NonNull
    public String getPlaybackNotice() {
        return playbackNotice;
    }
}
