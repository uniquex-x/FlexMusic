package com.example.core_domain.player;

import androidx.annotation.NonNull;

public final class PlaybackWarmupSnapshot {

    private final String sourceId;
    private final String originalUrl;
    private final String resolvedUrl;
    private final String localPlaybackUrl;
    private final String contentType;
    private final String userAgent;
    private final PlaybackWarmupLevel requestedLevel;
    private final PlaybackWarmupLevel completedLevel;
    private final boolean liveStream;
    private final boolean localSource;
    private final boolean seekable;
    private final boolean acceptRanges;
    private final long contentLength;
    private final long probeLatencyMs;
    private final long totalLatencyMs;

    public PlaybackWarmupSnapshot(@NonNull String sourceId,
                                  @NonNull String originalUrl,
                                  @NonNull String resolvedUrl,
                                  @NonNull String localPlaybackUrl,
                                  @NonNull String contentType,
                                  @NonNull String userAgent,
                                  @NonNull PlaybackWarmupLevel requestedLevel,
                                  @NonNull PlaybackWarmupLevel completedLevel,
                                  boolean liveStream,
                                  boolean localSource,
                                  boolean seekable,
                                  boolean acceptRanges,
                                  long contentLength,
                                  long probeLatencyMs,
                                  long totalLatencyMs) {
        this.sourceId = sourceId;
        this.originalUrl = originalUrl;
        this.resolvedUrl = resolvedUrl;
        this.localPlaybackUrl = localPlaybackUrl;
        this.contentType = contentType;
        this.userAgent = userAgent;
        this.requestedLevel = requestedLevel;
        this.completedLevel = completedLevel;
        this.liveStream = liveStream;
        this.localSource = localSource;
        this.seekable = seekable;
        this.acceptRanges = acceptRanges;
        this.contentLength = contentLength;
        this.probeLatencyMs = probeLatencyMs;
        this.totalLatencyMs = totalLatencyMs;
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
    public String getLocalPlaybackUrl() {
        return localPlaybackUrl;
    }

    @NonNull
    public String getContentType() {
        return contentType;
    }

    @NonNull
    public String getUserAgent() {
        return userAgent;
    }

    @NonNull
    public PlaybackWarmupLevel getRequestedLevel() {
        return requestedLevel;
    }

    @NonNull
    public PlaybackWarmupLevel getCompletedLevel() {
        return completedLevel;
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

    public boolean isAcceptRanges() {
        return acceptRanges;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getProbeLatencyMs() {
        return probeLatencyMs;
    }

    public long getTotalLatencyMs() {
        return totalLatencyMs;
    }
}
