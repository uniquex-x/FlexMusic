package com.example.core_network.search;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SpotifyAuthService {

    private static final String TAG = "SpotifyAuthService";
    private static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final long TOKEN_REFRESH_SKEW_MS = 60_000L;
    private static final RequestPolicy TOKEN_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(5_000)
            .readTimeoutMs(8_000)
            .retryCount(1)
            .build();

    private final NetworkClient networkClient;

    private String cachedAccessToken = "";
    private long cachedAccessTokenExpiresAtMs = 0L;

    public SpotifyAuthService() {
        this(NetworkClient.getInstance());
    }

    public SpotifyAuthService(@NonNull NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @NonNull
    public synchronized String getAccessToken() throws IOException {
        long now = System.currentTimeMillis();
        if (!cachedAccessToken.isEmpty() && cachedAccessTokenExpiresAtMs > now + TOKEN_REFRESH_SKEW_MS) {
            return cachedAccessToken;
        }
        String clientId = SpotifyApiConfig.requireClientId();
        String clientSecret = SpotifyApiConfig.requireClientSecret();
        String basicToken = Base64.encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Basic " + basicToken);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        String responseBody = networkClient.request(
                "POST",
                TOKEN_ENDPOINT,
                headers,
                "grant_type=client_credentials".getBytes(StandardCharsets.UTF_8),
                TOKEN_POLICY).getBody();
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            String accessToken = jsonObject.optString("access_token").trim();
            long expiresInSeconds = jsonObject.optLong("expires_in", 0L);
            if (accessToken.isEmpty()) {
                throw new IOException("Spotify token response missing access_token");
            }
            cachedAccessToken = accessToken;
            cachedAccessTokenExpiresAtMs = now + Math.max(0L, expiresInSeconds * 1000L);
            Log.d(TAG, "getAccessToken refreshed expiresInSeconds=" + expiresInSeconds);
            return cachedAccessToken;
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse Spotify token response", jsonException);
        }
    }
}
