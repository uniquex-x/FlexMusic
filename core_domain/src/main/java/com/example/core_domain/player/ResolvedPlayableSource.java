package com.example.core_domain.player;

import androidx.annotation.NonNull;

public final class ResolvedPlayableSource {

    private final String sourceId;
    private final String originalUrl;
    private final String resolvedUrl;
    private final String contentType;
    private final String userAgent;
    private final boolean liveStream;
    private final boolean localSource;
    private final boolean seekable;
    private final long probeLatencyMs;

    public ResolvedPlayableSource(@NonNull String sourceId,
                                  @NonNull String originalUrl,
                                  @NonNull String resolvedUrl,
                                  @NonNull String contentType,
                                  @NonNull String userAgent,
                                  boolean liveStream,
                                  boolean localSource,
                                  boolean seekable,
                                  long probeLatencyMs) {
        this.sourceId = sourceId;
        this.originalUrl = originalUrl;
        this.resolvedUrl = resolvedUrl;
        this.contentType = contentType;
        this.userAgent = userAgent;
        this.liveStream = liveStream;
        this.localSource = localSource;
        this.seekable = seekable;
        this.probeLatencyMs = probeLatencyMs;
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
    public String getResolvedUrl() {
        return resolvedUrl;
    }

    @NonNull
    public String getContentType() {
        return contentType;
    }

    @NonNull
    public String getUserAgent() {
        return userAgent;
    }

    public boolean isLiveStream() {
        return liveStream;
    }

    public boolean isLocalSource() {
        return localSource;
    }

    public boolean isSeekable() {
        return seekable;
    }

    public long getProbeLatencyMs() {
        return probeLatencyMs;
    }
}
