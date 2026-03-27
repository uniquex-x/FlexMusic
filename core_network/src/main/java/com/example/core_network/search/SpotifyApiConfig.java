package com.example.core_network.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_network.BuildConfig;

import java.io.IOException;
import java.util.Locale;

public final class SpotifyApiConfig {

    private SpotifyApiConfig() {
    }

    @NonNull
    public static String requireClientId() throws IOException {
        String clientId = BuildConfig.SPOTIFY_CLIENT_ID == null
                ? ""
                : BuildConfig.SPOTIFY_CLIENT_ID.trim();
        if (TextUtils.isEmpty(clientId)) {
            throw new IOException("Spotify client_id is not configured. Set spotify.clientId in local.properties, spotifyClientId in ~/.gradle/gradle.properties, or SPOTIFY_CLIENT_ID in the environment.");
        }
        return clientId;
    }

    @NonNull
    public static String requireClientSecret() throws IOException {
        String clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET == null
                ? ""
                : BuildConfig.SPOTIFY_CLIENT_SECRET.trim();
        if (TextUtils.isEmpty(clientSecret)) {
            throw new IOException("Spotify client_secret is not configured. Set spotify.clientSecret in local.properties, spotifyClientSecret in ~/.gradle/gradle.properties, or SPOTIFY_CLIENT_SECRET in the environment.");
        }
        return clientSecret;
    }

    @NonNull
    public static String resolveMarket() {
        String configuredMarket = BuildConfig.SPOTIFY_DEFAULT_MARKET == null
                ? ""
                : BuildConfig.SPOTIFY_DEFAULT_MARKET.trim().toUpperCase(Locale.ROOT);
        if (configuredMarket.length() == 2) {
            return configuredMarket;
        }
        String localeMarket = Locale.getDefault().getCountry();
        if (localeMarket != null && localeMarket.trim().length() == 2) {
            return localeMarket.trim().toUpperCase(Locale.ROOT);
        }
        return "US";
    }
}
