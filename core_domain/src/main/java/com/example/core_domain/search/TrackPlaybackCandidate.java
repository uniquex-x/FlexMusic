package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TrackPlaybackCandidate {

    private final String sourceId;
    private final String originalUrl;
    private final Map<String, String> headers;
    private final String qualityLabel;
    private final long expiresAtMs;
    private final boolean live;
    private final int confidence;

    public TrackPlaybackCandidate(@NonNull String sourceId,
                                  @NonNull String originalUrl,
                                  @NonNull Map<String, String> headers,
                                  @NonNull String qualityLabel,
                                  long expiresAtMs,
                                  boolean live,
                                  int confidence) {
        this.sourceId = sourceId;
        this.originalUrl = originalUrl;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.qualityLabel = qualityLabel;
        this.expiresAtMs = expiresAtMs;
        this.live = live;
        this.confidence = confidence;
    }

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public String getOriginalUrl() {
        return originalUrl;
    }

    @NonNull
    public Map<String, String> getHeaders() {
        return headers;
    }

    @NonNull
    public String getQualityLabel() {
        return qualityLabel;
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public boolean isLive() {
        return live;
    }

    public int getConfidence() {
        return confidence;
    }
}
