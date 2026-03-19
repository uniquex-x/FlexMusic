package com.example.core_domain.player;

import androidx.annotation.NonNull;

public enum PlaybackWarmupLevel {
    NONE,
    HOST,
    URL_METADATA,
    PROXY_SESSION,
    HEAD_CACHE,
    PLAYBACK_CANDIDATE;

    public boolean includes(@NonNull PlaybackWarmupLevel other) {
        return ordinal() >= other.ordinal();
    }
}
