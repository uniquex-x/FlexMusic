package com.example.core_network.stream;

import androidx.annotation.NonNull;

final class AudioStreamProbeResult {

    private final String resolvedUrl;
    private final String contentType;
    private final long contentLength;
    private final boolean acceptRanges;
    private final long probeLatencyMs;

    AudioStreamProbeResult(@NonNull String resolvedUrl,
                           @NonNull String contentType,
                           long contentLength,
                           boolean acceptRanges,
                           long probeLatencyMs) {
        this.resolvedUrl = resolvedUrl;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.acceptRanges = acceptRanges;
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

    long getContentLength() {
        return contentLength;
    }

    boolean isAcceptRanges() {
        return acceptRanges;
    }

    long getProbeLatencyMs() {
        return probeLatencyMs;
    }
}
