package com.example.core_network.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_network.BuildConfig;

import java.io.IOException;

public final class JamendoApiConfig {

    private JamendoApiConfig() {
    }

    @NonNull
    public static String requireClientId() throws IOException {
        String clientId = BuildConfig.JAMENDO_CLIENT_ID == null
                ? ""
                : BuildConfig.JAMENDO_CLIENT_ID.trim();
        if (TextUtils.isEmpty(clientId)) {
            throw new IOException("Jamendo client_id is not configured. Set jamendo.clientId in local.properties, jamendoClientId in ~/.gradle/gradle.properties, or JAMENDO_CLIENT_ID in the environment.");
        }
        return clientId;
    }
}
