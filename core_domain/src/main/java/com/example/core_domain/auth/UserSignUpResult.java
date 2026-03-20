package com.example.core_domain.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UserSignUpResult {

    private final boolean activeSession;
    private final boolean emailConfirmationPending;
    private final UserAccountProfile profile;

    public UserSignUpResult(boolean activeSession,
                            boolean emailConfirmationPending,
                            @Nullable UserAccountProfile profile) {
        this.activeSession = activeSession;
        this.emailConfirmationPending = emailConfirmationPending;
        this.profile = profile;
    }

    public boolean hasActiveSession() {
        return activeSession;
    }

    public boolean isEmailConfirmationPending() {
        return emailConfirmationPending;
    }

    @Nullable
    public UserAccountProfile getProfile() {
        return profile;
    }

    @NonNull
    public static UserSignUpResult pendingConfirmation() {
        return new UserSignUpResult(false, true, null);
    }

    @NonNull
    public static UserSignUpResult authenticated(@NonNull UserAccountProfile profile) {
        return new UserSignUpResult(true, false, profile);
    }
}
