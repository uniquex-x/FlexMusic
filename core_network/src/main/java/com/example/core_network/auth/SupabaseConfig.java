package com.example.core_network.auth;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_network.BuildConfig;

public final class SupabaseConfig {

    private static final String AVATARS_BUCKET = "avatars";

    private SupabaseConfig() {
    }

    @NonNull
    public static String requireProjectUrl() {
        if (TextUtils.isEmpty(BuildConfig.SUPABASE_URL)) {
            throw new IllegalStateException("Supabase project URL is not configured");
        }
        return BuildConfig.SUPABASE_URL;
    }

    @NonNull
    public static String requirePublishableKey() {
        if (TextUtils.isEmpty(BuildConfig.SUPABASE_PUBLISHABLE_KEY)) {
            throw new IllegalStateException("Supabase publishable key is not configured");
        }
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    }

    @NonNull
    public static String getAuthEndpoint(@NonNull String relativePath) {
        return Uri.parse(requireProjectUrl() + "/auth/v1/" + relativePath).toString();
    }

    @NonNull
    public static String getRestEndpoint(@NonNull String relativePath) {
        return Uri.parse(requireProjectUrl() + "/rest/v1/" + relativePath).toString();
    }

    @NonNull
    public static String getStorageObjectEndpoint(@NonNull String objectPath) {
        String encodedPath = Uri.encode(objectPath, "/");
        return requireProjectUrl() + "/storage/v1/object/" + AVATARS_BUCKET + "/" + encodedPath;
    }

    @NonNull
    public static String getPublicAvatarUrl(@NonNull String objectPath) {
        String encodedPath = Uri.encode(objectPath, "/");
        return requireProjectUrl() + "/storage/v1/object/public/" + AVATARS_BUCKET + "/" + encodedPath;
    }
}
