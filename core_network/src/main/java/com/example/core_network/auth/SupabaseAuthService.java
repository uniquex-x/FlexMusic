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

public class SupabaseAuthService {

    private static final String TAG = "SupabaseAuthService";
    private static final RequestPolicy AUTH_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(8_000)
            .readTimeoutMs(10_000)
            .retryCount(0)
            .build();

    private final NetworkClient networkClient;

    public SupabaseAuthService() {
        this(NetworkClient.getInstance());
    }

    public SupabaseAuthService(@NonNull NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @NonNull
    public SupabaseAuthResultDto signUp(@NonNull String email,
                                        @NonNull String password,
                                        @Nullable String username) throws IOException {
        JSONObject payload = new JSONObject();
        JSONObject metadata = new JSONObject();
        try {
            payload.put("email", email.trim());
            payload.put("password", password);
            if (!TextUtils.isEmpty(username)) {
                metadata.put("username", username.trim());
            }
            if (metadata.length() > 0) {
                payload.put("data", metadata);
            }
        } catch (JSONException jsonException) {
            throw new IOException("Failed to encode sign up payload", jsonException);
        }
        Log.d(TAG, "signUp email=" + email.trim());
        return parseAuthResult(postJson(SupabaseConfig.getAuthEndpoint("signup"), payload.toString(), null));
    }

    @NonNull
    public SupabaseAuthSessionDto signIn(@NonNull String email,
                                         @NonNull String password) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("email", email.trim());
            payload.put("password", password);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to encode sign in payload", jsonException);
        }
        String endpoint = Uri.parse(SupabaseConfig.getAuthEndpoint("token"))
                .buildUpon()
                .appendQueryParameter("grant_type", "password")
                .build()
                .toString();
        Log.d(TAG, "signIn email=" + email.trim());
        SupabaseAuthResultDto result = parseAuthResult(postJson(endpoint, payload.toString(), null));
        if (result.getSession() == null) {
            throw new IOException("Sign in completed without an active session");
        }
        return result.getSession();
    }

    @NonNull
    public SupabaseAuthSessionDto refreshSession(@NonNull String refreshToken) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("refresh_token", refreshToken);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to encode refresh payload", jsonException);
        }
        String endpoint = Uri.parse(SupabaseConfig.getAuthEndpoint("token"))
                .buildUpon()
                .appendQueryParameter("grant_type", "refresh_token")
                .build()
                .toString();
        Log.d(TAG, "refreshSession");
        SupabaseAuthResultDto result = parseAuthResult(postJson(endpoint, payload.toString(), null));
        if (result.getSession() == null) {
            throw new IOException("Session refresh completed without an active session");
        }
        return result.getSession();
    }

    @NonNull
    public SupabaseUserDto getUser(@NonNull String accessToken) throws IOException {
        Log.d(TAG, "getUser");
        String responseBody;
        try {
            responseBody = networkClient.get(
                    SupabaseConfig.getAuthEndpoint("user"),
                    buildHeaders(accessToken),
                    AUTH_POLICY);
        } catch (IOException ioException) {
            throw new IOException(SupabaseErrorParser.extractMessage(
                    ioException,
                    "Failed to load current user"), ioException);
        }
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            SupabaseUserDto user;
            if (rootObject.has("user")) {
                user = parseUser(rootObject.optJSONObject("user"));
            } else {
                user = parseUser(rootObject);
            }
            if (user == null) {
                throw new IOException("Current session user is missing");
            }
            return user;
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse current user response", jsonException);
        }
    }

    public void signOut(@NonNull String accessToken) throws IOException {
        Log.d(TAG, "signOut");
        JSONObject payload = new JSONObject();
        try {
            payload.put("scope", "local");
        } catch (JSONException jsonException) {
            throw new IOException("Failed to encode sign out payload", jsonException);
        }
        postJson(SupabaseConfig.getAuthEndpoint("logout"), payload.toString(), accessToken);
    }

    @NonNull
    private String postJson(@NonNull String url,
                            @NonNull String body,
                            @Nullable String accessToken) throws IOException {
        try {
            return networkClient.request(
                    "POST",
                    url,
                    buildJsonHeaders(accessToken),
                    body.getBytes(StandardCharsets.UTF_8),
                    AUTH_POLICY)
                    .getBody();
        } catch (IOException ioException) {
            throw new IOException(SupabaseErrorParser.extractMessage(
                    ioException,
                    "Supabase auth request failed"), ioException);
        }
    }

    @NonNull
    private SupabaseAuthResultDto parseAuthResult(@NonNull String responseBody) throws IOException {
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            JSONObject sessionObject = rootObject.optJSONObject("session");
            JSONObject userObject = rootObject.optJSONObject("user");
            if (sessionObject == null
                    && (rootObject.has("access_token") || rootObject.has("refresh_token"))) {
                sessionObject = rootObject;
            }
            if (userObject == null && sessionObject != null) {
                userObject = sessionObject.optJSONObject("user");
            }
            SupabaseUserDto user = userObject != null ? parseUser(userObject) : null;
            SupabaseAuthSessionDto session = sessionObject != null ? parseSession(sessionObject, user) : null;
            return new SupabaseAuthResultDto(session, user);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse auth response", jsonException);
        }
    }

    @NonNull
    private SupabaseAuthSessionDto parseSession(@NonNull JSONObject sessionObject,
                                                @Nullable SupabaseUserDto fallbackUser) throws IOException {
        try {
            SupabaseUserDto user = fallbackUser;
            if (user == null) {
                user = parseUser(sessionObject.optJSONObject("user"));
            }
            if (user == null) {
                throw new IOException("Auth session is missing user information");
            }
            long expiresAt = sessionObject.optLong("expires_at", 0L);
            if (expiresAt <= 0L) {
                long expiresIn = sessionObject.optLong("expires_in", 0L);
                expiresAt = (System.currentTimeMillis() / 1000L) + Math.max(expiresIn, 0L);
            }
            return new SupabaseAuthSessionDto(
                    sessionObject.optString("access_token"),
                    sessionObject.optString("refresh_token"),
                    expiresAt,
                    user);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse auth session", jsonException);
        }
    }

    @Nullable
    private SupabaseUserDto parseUser(@Nullable JSONObject userObject) throws JSONException {
        if (userObject == null) {
            return null;
        }
        JSONObject metadata = userObject.optJSONObject("user_metadata");
        if (metadata == null) {
            metadata = userObject.optJSONObject("raw_user_meta_data");
        }
        String username = metadata != null ? trimToNull(metadata.optString("username")) : null;
        return new SupabaseUserDto(
                userObject.optString("id"),
                userObject.optString("email"),
                username);
    }

    @NonNull
    private Map<String, String> buildHeaders(@Nullable String accessToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", SupabaseConfig.requirePublishableKey());
        headers.put("Accept", "application/json");
        headers.put("X-Client-Info", "flexmusic-android/1.0");
        if (!TextUtils.isEmpty(accessToken)) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return headers;
    }

    @NonNull
    private Map<String, String> buildJsonHeaders(@Nullable String accessToken) {
        Map<String, String> headers = buildHeaders(accessToken);
        headers.put("Content-Type", "application/json; charset=utf-8");
        return headers;
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
