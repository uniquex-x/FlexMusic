package com.example.core_data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_network.auth.SupabaseAuthSessionDto;

class AuthSessionStore {

    private static final String PREFS_NAME = "flexmusic_supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;

    AuthSessionStore(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    void save(@NonNull SupabaseAuthSessionDto session) {
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, session.getAccessToken())
                .putString(KEY_REFRESH_TOKEN, session.getRefreshToken())
                .putLong(KEY_EXPIRES_AT, session.getExpiresAtEpochSeconds())
                .putString(KEY_USER_ID, session.getUser().getId())
                .putString(KEY_EMAIL, session.getUser().getEmail())
                .apply();
    }

    @Nullable
    StoredSession load() {
        String accessToken = preferences.getString(KEY_ACCESS_TOKEN, null);
        String refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null);
        String userId = preferences.getString(KEY_USER_ID, null);
        String email = preferences.getString(KEY_EMAIL, null);
        long expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L);
        if (TextUtils.isEmpty(accessToken)
                || TextUtils.isEmpty(refreshToken)
                || TextUtils.isEmpty(userId)
                || TextUtils.isEmpty(email)) {
            return null;
        }
        return new StoredSession(accessToken, refreshToken, expiresAt, userId, email);
    }

    void clear() {
        preferences.edit().clear().apply();
    }

    static class StoredSession {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresAtEpochSeconds;
        private final String userId;
        private final String email;

        StoredSession(@NonNull String accessToken,
                      @NonNull String refreshToken,
                      long expiresAtEpochSeconds,
                      @NonNull String userId,
                      @NonNull String email) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
            this.userId = userId;
            this.email = email;
        }

        @NonNull
        String getAccessToken() {
            return accessToken;
        }

        @NonNull
        String getRefreshToken() {
            return refreshToken;
        }

        long getExpiresAtEpochSeconds() {
            return expiresAtEpochSeconds;
        }

        @NonNull
        String getUserId() {
            return userId;
        }

        @NonNull
        String getEmail() {
            return email;
        }
    }
}
