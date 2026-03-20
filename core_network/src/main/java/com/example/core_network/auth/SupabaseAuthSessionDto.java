package com.example.core_network.auth;

import androidx.annotation.NonNull;

public class SupabaseAuthSessionDto {

    private final String accessToken;
    private final String refreshToken;
    private final long expiresAtEpochSeconds;
    private final SupabaseUserDto user;

    public SupabaseAuthSessionDto(@NonNull String accessToken,
                                  @NonNull String refreshToken,
                                  long expiresAtEpochSeconds,
                                  @NonNull SupabaseUserDto user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        this.user = user;
    }

    @NonNull
    public String getAccessToken() {
        return accessToken;
    }

    @NonNull
    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    @NonNull
    public SupabaseUserDto getUser() {
        return user;
    }
}
