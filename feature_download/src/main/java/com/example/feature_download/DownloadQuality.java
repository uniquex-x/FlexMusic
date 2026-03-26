package com.example.feature_download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum DownloadQuality {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String storageValue;

    DownloadQuality(@NonNull String storageValue) {
        this.storageValue = storageValue;
    }

    @NonNull
    public String getStorageValue() {
        return storageValue;
    }

    @NonNull
    public static DownloadQuality fromStorageValue(@Nullable String storageValue) {
        if (LOW.storageValue.equalsIgnoreCase(storageValue)) {
            return LOW;
        }
        if (MEDIUM.storageValue.equalsIgnoreCase(storageValue)) {
            return MEDIUM;
        }
        return HIGH;
    }
}
