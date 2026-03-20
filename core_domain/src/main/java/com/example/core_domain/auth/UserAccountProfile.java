package com.example.core_domain.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UserAccountProfile {

    private final String id;
    private final String email;
    private final String username;
    private final String avatarUrl;
    private final String avatarPath;

    public UserAccountProfile(@NonNull String id,
                              @NonNull String email,
                              @Nullable String username,
                              @Nullable String avatarUrl,
                              @Nullable String avatarPath) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.avatarPath = avatarPath;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getEmail() {
        return email;
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
