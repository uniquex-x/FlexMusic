package com.example.core_domain.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UserRegistrationRequest {

    private final String email;
    private final String password;
    private final String username;

    public UserRegistrationRequest(@NonNull String email,
                                   @NonNull String password,
                                   @Nullable String username) {
        this.email = email;
        this.password = password;
        this.username = username;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    @NonNull
    public String getPassword() {
        return password;
    }

    @Nullable
    public String getUsername() {
        return username;
    }
}
