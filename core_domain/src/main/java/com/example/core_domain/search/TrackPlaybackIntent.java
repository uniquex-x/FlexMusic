package com.example.core_domain.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class TrackPlaybackIntent {

    private final String trackId;
    private final String providerId;
    private final String albumId;
    private final String preferredQuality;
    private final boolean requiresResolve;
    private final String candidateToken;

    public TrackPlaybackIntent(@NonNull String trackId,
                               @NonNull String providerId,
                               @Nullable String albumId,
                               @Nullable String preferredQuality,
                               boolean requiresResolve,
                               @Nullable String candidateToken) {
        this.trackId = trackId;
        this.providerId = providerId;
        this.albumId = albumId == null ? "" : albumId;
        this.preferredQuality = preferredQuality == null ? "" : preferredQuality;
        this.requiresResolve = requiresResolve;
        this.candidateToken = candidateToken == null ? "" : candidateToken;
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
}
