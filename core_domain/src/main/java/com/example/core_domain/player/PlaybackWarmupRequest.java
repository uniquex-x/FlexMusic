package com.example.core_domain.player;

import androidx.annotation.NonNull;

public final class PlaybackWarmupRequest {

    private final String sourceId;
    private final String originalUrl;
    private final boolean liveStream;
    private final PlaybackWarmupLevel targetLevel;

    public PlaybackWarmupRequest(@NonNull String sourceId,
                                 @NonNull String originalUrl,
                                 boolean liveStream,
                                 @NonNull PlaybackWarmupLevel targetLevel) {
        this.sourceId = sourceId;
        this.originalUrl = originalUrl;
        this.liveStream = liveStream;
        this.targetLevel = targetLevel;
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

    @NonNull
    public PlaybackWarmupLevel getTargetLevel() {
        return targetLevel;
    }
}
