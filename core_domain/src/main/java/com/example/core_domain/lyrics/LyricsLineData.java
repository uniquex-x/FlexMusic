package com.example.core_domain.lyrics;

import androidx.annotation.NonNull;

public final class LyricsLineData {

    private final long timestampMs;
    private final String text;

    public LyricsLineData(long timestampMs, @NonNull String text) {
        this.timestampMs = Math.max(0L, timestampMs);
        this.text = text;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    @NonNull
    public String getText() {
        return text;
    }
}
