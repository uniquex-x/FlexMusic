package com.example.feature_download;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

final class DownloadSettingsStore {

    private static final String PREFS_NAME = "flexmusic_download_settings";
    private static final String KEY_QUALITY = "download_quality";

    private final SharedPreferences preferences;

    DownloadSettingsStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    DownloadQuality getPreferredQuality() {
        return DownloadQuality.fromStorageValue(preferences.getString(
                KEY_QUALITY,
                DownloadQuality.HIGH.getStorageValue()));
    }

    void setPreferredQuality(@NonNull DownloadQuality quality) {
        preferences.edit().putString(KEY_QUALITY, quality.getStorageValue()).apply();
    }
}
