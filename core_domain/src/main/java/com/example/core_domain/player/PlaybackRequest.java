package com.example.core_domain.player;

import androidx.annotation.NonNull;

public final class PlaybackRequest {

    private final String sourceId;
    private final String originalUrl;
    private final boolean liveStream;

    public PlaybackRequest(@NonNull String sourceId,
                           @NonNull String originalUrl,
                           boolean liveStream) {
        this.sourceId = sourceId;
        this.originalUrl = originalUrl;
        this.liveStream = liveStream;
    }

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public String getOriginalUrl() {
        return originalUrl;
    }

    public boolean isLiveStream() {
        return liveStream;
    }
}
