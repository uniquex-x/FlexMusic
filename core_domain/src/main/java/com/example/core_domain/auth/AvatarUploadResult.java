package com.example.core_domain.auth;

import androidx.annotation.NonNull;

public class AvatarUploadResult {

    private final String avatarPath;
    private final String avatarUrl;

    public AvatarUploadResult(@NonNull String avatarPath,
                              @NonNull String avatarUrl) {
        this.avatarPath = avatarPath;
        this.avatarUrl = avatarUrl;
    }

    @NonNull
    public String getAvatarPath() {
        return avatarPath;
    }

    @NonNull
    public String getAvatarUrl() {
        return avatarUrl;
    }
}
