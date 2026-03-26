package com.example.feature_download;

import androidx.annotation.NonNull;

public final class DownloadRecord {

    private final String sourceId;
    private final String sourceUrl;
    private final String localPath;
    private final String fileName;
    private final long downloadedAtMs;

    public DownloadRecord(@NonNull String sourceId,
                          @NonNull String sourceUrl,
                          @NonNull String localPath,
                          @NonNull String fileName,
                          long downloadedAtMs) {
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
        this.localPath = localPath;
        this.fileName = fileName;
        this.downloadedAtMs = downloadedAtMs;
    }

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public String getSourceUrl() {
        return sourceUrl;
    }

    @NonNull
    public String getLocalPath() {
        return localPath;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    public long getDownloadedAtMs() {
        return downloadedAtMs;
    }
}
