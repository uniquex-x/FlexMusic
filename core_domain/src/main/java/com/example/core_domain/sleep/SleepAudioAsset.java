package com.example.core_domain.sleep;

import androidx.annotation.NonNull;

public class SleepAudioAsset {

    private final String id;
    private final String title;
    private final String remoteUrl;
    private final String fileExtension;

    public SleepAudioAsset(@NonNull String id,
                           @NonNull String title,
                           @NonNull String remoteUrl,
                           @NonNull String fileExtension) {
        this.id = id;
        this.title = title;
        this.remoteUrl = remoteUrl;
        this.fileExtension = fileExtension;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getRemoteUrl() {
        return remoteUrl;
    }

    @NonNull
    public String getFileExtension() {
        return fileExtension;
    }
}
