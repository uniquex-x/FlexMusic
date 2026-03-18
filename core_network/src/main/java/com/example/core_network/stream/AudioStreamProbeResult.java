package com.example.core_network.stream;

import androidx.annotation.NonNull;

final class AudioStreamProbeResult {

    private final String resolvedUrl;
    private final String contentType;
    private final long probeLatencyMs;

    AudioStreamProbeResult(@NonNull String resolvedUrl,
                           @NonNull String contentType,
                           long probeLatencyMs) {
        this.resolvedUrl = resolvedUrl;
        this.contentType = contentType;
        this.probeLatencyMs = probeLatencyMs;
    }

    @NonNull
    String getResolvedUrl() {
        return resolvedUrl;
    }

    @NonNull
    String getContentType() {
        return contentType;
    }

    long getProbeLatencyMs() {
        return probeLatencyMs;
    }
}
