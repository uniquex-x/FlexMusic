package com.example.core_network.auth;

import androidx.annotation.Nullable;

public class SupabaseAuthResultDto {

    private final SupabaseAuthSessionDto session;
    private final SupabaseUserDto user;

    public SupabaseAuthResultDto(@Nullable SupabaseAuthSessionDto session,
                                 @Nullable SupabaseUserDto user) {
        this.session = session;
        this.user = user;
    }

    @Nullable
    public SupabaseAuthSessionDto getSession() {
        return session;
    }

    @Nullable
    public SupabaseUserDto getUser() {
        return user;
    }

    public boolean hasActiveSession() {
        return session != null;
    }
}
