package com.example.core_network.auth;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SupabaseStorageService {

    private static final String TAG = "SupabaseStorageService";
    private static final RequestPolicy STORAGE_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(10_000)
            .readTimeoutMs(15_000)
            .retryCount(0)
            .build();

    private final NetworkClient networkClient;

    public SupabaseStorageService() {
        this(NetworkClient.getInstance());
    }

    public SupabaseStorageService(@NonNull NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @NonNull
    public SupabaseStorageObjectDto uploadAvatar(@NonNull String accessToken,
                                                 @NonNull String userId,
                                                 @NonNull String fileName,
                                                 @NonNull String contentType,
                                                 @NonNull byte[] data) throws IOException {
        String safeFileName = sanitizeFileName(fileName);
        String objectPath = userId + "/" + safeFileName;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", SupabaseConfig.requirePublishableKey());
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", TextUtils.isEmpty(contentType)
                ? "application/octet-stream"
                : contentType);
        headers.put("x-upsert", "true");
        headers.put("X-Client-Info", "flexmusic-android/1.0");
        Log.d(TAG, "uploadAvatar userId=" + userId + " objectPath=" + objectPath);
        try {
            networkClient.request(
                    "POST",
                    SupabaseConfig.getStorageObjectEndpoint(objectPath),
                    headers,
                    data,
                    STORAGE_POLICY);
        } catch (IOException ioException) {
            throw new IOException(SupabaseErrorParser.extractMessage(
                    ioException,
                    "Failed to upload avatar"), ioException);
        }
        String publicUrl = SupabaseConfig.getPublicAvatarUrl(objectPath);
        return new SupabaseStorageObjectDto(objectPath, publicUrl);
    }

    @NonNull
    private String sanitizeFileName(@NonNull String fileName) {
        String sanitized = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isEmpty()) {
            return "avatar.bin";
        }
        return sanitized;
    }
}
