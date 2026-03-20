package com.example.core_network.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SupabaseUserDto {

    private final String id;
    private final String email;
    private final String username;

    public SupabaseUserDto(@NonNull String id,
                           @NonNull String email,
                           @Nullable String username) {
        this.id = id;
        this.email = email;
        this.username = username;
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
}
