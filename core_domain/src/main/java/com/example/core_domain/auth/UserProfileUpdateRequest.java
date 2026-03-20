package com.example.core_domain.auth;

import androidx.annotation.Nullable;

public class UserProfileUpdateRequest {

    private final String username;
    private final String avatarUrl;
    private final String avatarPath;

    public UserProfileUpdateRequest(@Nullable String username,
                                    @Nullable String avatarUrl,
                                    @Nullable String avatarPath) {
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.avatarPath = avatarPath;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @Nullable
    public String getAvatarUrl() {
        return avatarUrl;
    }

    @Nullable
    public String getAvatarPath() {
        return avatarPath;
    }
}
