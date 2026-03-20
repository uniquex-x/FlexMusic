package com.example.core_domain.sleep;

import androidx.annotation.NonNull;

public class SleepAudioPlaybackSource {

    private final String source;
    private final boolean cached;

    public SleepAudioPlaybackSource(@NonNull String source, boolean cached) {
        this.source = source;
        this.cached = cached;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    public boolean isCached() {
        return cached;
    }
}
