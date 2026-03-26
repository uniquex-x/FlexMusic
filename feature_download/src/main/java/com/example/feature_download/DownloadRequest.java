package com.example.feature_download;

import androidx.annotation.NonNull;

public final class DownloadRequest {

    private final String sourceId;
    private final String displayName;
    private final String sourceUrl;
    private final String userAgent;
    private final boolean liveStream;

    public DownloadRequest(@NonNull String sourceId,
                           @NonNull String displayName,
                           @NonNull String sourceUrl,
                           @NonNull String userAgent,
                           boolean liveStream) {
        this.sourceId = sourceId;
        this.displayName = displayName;
        this.sourceUrl = sourceUrl;
        this.userAgent = userAgent;
        this.liveStream = liveStream;
    }

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getSourceUrl() {
        return sourceUrl;
    }

    @NonNull
    public String getUserAgent() {
        return userAgent;
    }

    public boolean isLiveStream() {
        return liveStream;
    }
}
