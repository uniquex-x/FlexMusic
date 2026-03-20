package com.example.core_network.auth;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class SupabaseProfileService {

    private static final String TAG = "SupabaseProfileService";
    private static final RequestPolicy PROFILE_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(8_000)
            .readTimeoutMs(10_000)
            .retryCount(0)
            .build();

    private final NetworkClient networkClient;

    public SupabaseProfileService() {
        this(NetworkClient.getInstance());
    }

    public SupabaseProfileService(@NonNull NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @Nullable
    public SupabaseProfileDto getProfile(@NonNull String accessToken,
                                         @NonNull String userId) throws IOException {
        String requestUrl = Uri.parse(SupabaseConfig.getRestEndpoint("profiles"))
                .buildUpon()
                .appendQueryParameter("select", "id,username,avatar_url,avatar_path")
                .appendQueryParameter("id", "eq." + userId)
                .build()
                .toString();
        Log.d(TAG, "getProfile userId=" + userId);
        String responseBody;
        try {
            responseBody = networkClient.get(requestUrl, buildHeaders(accessToken), PROFILE_POLICY);
        } catch (IOException ioException) {
            throw new IOException(SupabaseErrorParser.extractMessage(
                    ioException,
                    "Failed to load profile"), ioException);
        }
        try {
            JSONArray array = new JSONArray(responseBody);
            if (array.length() == 0) {
                return null;
            }
            return parseProfile(array.getJSONObject(0));
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse profile response", jsonException);
        }
    }

    @NonNull
    public SupabaseProfileDto upsertProfile(@NonNull String accessToken,
                                            @NonNull String userId,
                                            @Nullable String username,
                                            @Nullable String avatarUrl,
                                            @Nullable String avatarPath) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", userId);
            payload.put("username", normalizeNullable(username));
            payload.put("avatar_url", normalizeNullable(avatarUrl));
            payload.put("avatar_path", normalizeNullable(avatarPath));
        } catch (JSONException jsonException) {
            throw new IOException("Failed to encode profile payload", jsonException);
        }
        String requestUrl = Uri.parse(SupabaseConfig.getRestEndpoint("profiles"))
                .buildUpon()
                .appendQueryParameter("on_conflict", "id")
                .appendQueryParameter("select", "id,username,avatar_url,avatar_path")
                .build()
                .toString();
        Map<String, String> headers = buildHeaders(accessToken);
        headers.put("Prefer", "resolution=merge-duplicates,return=representation");
        Log.d(TAG, "upsertProfile userId=" + userId + " username=" + normalizeNullable(username));
        try {
            String responseBody = networkClient.request(
                    "POST",
                    requestUrl,
                    headers,
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    PROFILE_POLICY)
                    .getBody();
            JSONArray array = new JSONArray(responseBody);
            if (array.length() == 0) {
                throw new IOException("Profile upsert returned no rows");
            }
            return parseProfile(array.getJSONObject(0));
        } catch (IOException ioException) {
            throw new IOException(SupabaseErrorParser.extractMessage(
                    ioException,
                    "Failed to save profile"), ioException);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse profile upsert response", jsonException);
        }
    }

    @NonNull
    private SupabaseProfileDto parseProfile(@NonNull JSONObject object) {
        return new SupabaseProfileDto(
                object.optString("id"),
                normalizeNullable(object.optString("username")),
                normalizeNullable(object.optString("avatar_url")),
                normalizeNullable(object.optString("avatar_path")));
    }

    @NonNull
    private Map<String, String> buildHeaders(@NonNull String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", SupabaseConfig.requirePublishableKey());
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-Client-Info", "flexmusic-android/1.0");
        return headers;
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
