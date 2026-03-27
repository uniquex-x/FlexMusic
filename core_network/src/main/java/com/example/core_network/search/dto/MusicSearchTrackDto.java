package com.example.core_network.search.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MusicSearchTrackDto {

    private final String trackId;
    private final String providerId;
    private final String albumId;
    private final String title;
    private final String subtitle;
    private final List<String> artistNames;
    private final String albumName;
    private final long durationMs;
    private final String coverUrl;
    private final String availability;
    private final String qualitySummary;
    private final String preferredQuality;
    private final boolean requiresResolve;
    private final String candidateToken;
    private final String streamUrl;
    private final boolean previewPlayback;
    private final String playbackNotice;

    public MusicSearchTrackDto(@NonNull String trackId,
                               @NonNull String providerId,
                               @Nullable String albumId,
                               @NonNull String title,
                               @NonNull String subtitle,
                               @NonNull List<String> artistNames,
                               @NonNull String albumName,
                               long durationMs,
                               @NonNull String coverUrl,
                               @NonNull String availability,
                               @NonNull String qualitySummary,
                               @NonNull String preferredQuality,
                               boolean requiresResolve,
                               @Nullable String candidateToken,
                               @Nullable String streamUrl,
                               boolean previewPlayback,
                               @Nullable String playbackNotice) {
        this.trackId = trackId;
        this.providerId = providerId;
        this.albumId = albumId == null ? "" : albumId;
        this.title = title;
        this.subtitle = subtitle;
        this.artistNames = Collections.unmodifiableList(new ArrayList<>(artistNames));
        this.albumName = albumName;
        this.durationMs = Math.max(0L, durationMs);
        this.coverUrl = coverUrl;
        this.availability = availability;
        this.qualitySummary = qualitySummary;
        this.preferredQuality = preferredQuality;
        this.requiresResolve = requiresResolve;
        this.candidateToken = candidateToken == null ? "" : candidateToken;
        this.streamUrl = streamUrl == null ? "" : streamUrl;
        this.previewPlayback = previewPlayback;
        this.playbackNotice = playbackNotice == null ? "" : playbackNotice;
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
    public String getAlbumId() {
        return albumId;
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
    public String getAvailability() {
        return availability;
    }

    @NonNull
    public String getQualitySummary() {
        return qualitySummary;
    }

    @NonNull
    public String getPreferredQuality() {
        return preferredQuality;
    }

    public boolean isRequiresResolve() {
        return requiresResolve;
    }

    @NonNull
    public String getCandidateToken() {
        return candidateToken;
    }

    @NonNull
    public String getStreamUrl() {
        return streamUrl;
    }

    public boolean isPreviewPlayback() {
        return previewPlayback;
    }

    @NonNull
    public String getPlaybackNotice() {
        return playbackNotice;
    }
}
