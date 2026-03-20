package com.example.core_network.auth;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class SupabaseAccountLookupService {

    private static final String TAG = "AccountLookupService";
    private static final RequestPolicy LOOKUP_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(6_000)
            .readTimeoutMs(8_000)
            .retryCount(0)
            .build();

    private final NetworkClient networkClient;

    public SupabaseAccountLookupService() {
        this(NetworkClient.getInstance());
    }

    public SupabaseAccountLookupService(@NonNull NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @Nullable
    public String resolveSignInEmail(@NonNull String identifier) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("p_identifier", identifier.trim());
        } catch (JSONException jsonException) {
            throw new IOException("Failed to encode account lookup payload", jsonException);
        }
        String requestUrl = Uri.parse(SupabaseConfig.getRestEndpoint("rpc/resolve_sign_in_email"))
                .buildUpon()
                .build()
                .toString();
        Log.d(TAG, "resolveSignInEmail identifier=" + identifier.trim());
        try {
            String responseBody = networkClient.request(
                    "POST",
                    requestUrl,
                    buildHeaders(),
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    LOOKUP_POLICY)
                    .getBody();
            String normalized = normalizeNullable(responseBody);
            if (!TextUtils.isEmpty(normalized)) {
                return normalized;
            }
            if ("null".equalsIgnoreCase(responseBody.trim())) {
                return null;
            }
            return null;
        } catch (IOException ioException) {
            throw new IOException(SupabaseErrorParser.extractMessage(
                    ioException,
                    "Failed to resolve account"), ioException);
        }
    }

    @NonNull
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", SupabaseConfig.requirePublishableKey());
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-Client-Info", "flexmusic-android/1.0");
        return headers;
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
