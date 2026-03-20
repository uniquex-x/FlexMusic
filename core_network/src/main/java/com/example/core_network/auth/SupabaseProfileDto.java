package com.example.core_network.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SupabaseProfileDto {

    private final String id;
    private final String username;
    private final String avatarUrl;
    private final String avatarPath;

    public SupabaseProfileDto(@NonNull String id,
                              @Nullable String username,
                              @Nullable String avatarUrl,
                              @Nullable String avatarPath) {
        this.id = id;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.avatarPath = avatarPath;
    }

    @NonNull
    public String getId() {
        return id;
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
