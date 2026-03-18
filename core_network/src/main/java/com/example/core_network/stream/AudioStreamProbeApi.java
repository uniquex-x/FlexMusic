package com.example.core_network.stream;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_network.http.RequestPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class AudioStreamProbeApi {

    public static final String DEFAULT_USER_AGENT = "FlexMusic/1.0";

    private static final RequestPolicy PROBE_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(2_000)
            .readTimeoutMs(2_500)
            .retryCount(0)
            .build();
    private static final List<String> LIGHTWEIGHT_METHODS = Arrays.asList("HEAD", "GET");

    @NonNull
    AudioStreamProbeResult probe(@NonNull String url) throws IOException {
        IOException lastError = null;
        for (String method : LIGHTWEIGHT_METHODS) {
            try {
                return executeLightweightProbe(url, method);
            } catch (IOException error) {
                lastError = error;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Stream probe failed for " + url);
    }

    @NonNull
    private AudioStreamProbeResult executeLightweightProbe(@NonNull String url,
                                                           @NonNull String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(method);
        connection.setUseCaches(false);
        connection.setConnectTimeout(PROBE_POLICY.getConnectTimeoutMs());
        connection.setReadTimeout(PROBE_POLICY.getReadTimeoutMs());
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
        connection.setRequestProperty("Icy-MetaData", "1");
        if ("GET".equals(method)) {
            connection.setRequestProperty("Range", "bytes=0-1");
        }

        long startAt = SystemClock.elapsedRealtime();
        InputStream inputStream = null;
        try {
            int code = connection.getResponseCode();
            if (!isSuccessCode(method, code)) {
                throw new IOException("HTTP " + code + " for " + method + " probe " + url);
            }

            if ("GET".equals(method)) {
                inputStream = connection.getInputStream();
            }

            String resolvedUrl = connection.getURL() != null ? connection.getURL().toString() : url;
            String contentType = connection.getContentType();
            return new AudioStreamProbeResult(
                    resolvedUrl,
                    contentType == null ? "" : contentType,
                    SystemClock.elapsedRealtime() - startAt);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
            connection.disconnect();
        }
    }

    private boolean isSuccessCode(@NonNull String method, int code) {
        if ("GET".equals(method)) {
            return (code >= 200 && code < 300) || code == HttpURLConnection.HTTP_PARTIAL;
        }
        return code >= 200 && code < 300;
    }

    static boolean isNetworkUri(@NonNull String url) {
        String scheme = Uri.parse(url).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    static boolean isLocalUri(@NonNull String url) {
        String scheme = Uri.parse(url).getScheme();
        return TextUtils.isEmpty(scheme)
                || "file".equalsIgnoreCase(scheme)
                || "content".equalsIgnoreCase(scheme)
                || "android.resource".equalsIgnoreCase(scheme);
    }
}
