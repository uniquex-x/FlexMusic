package com.example.core_network.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class NetworkClient {

    private static final NetworkClient INSTANCE = new NetworkClient();

    private NetworkClient() {
    }

    @NonNull
    public static NetworkClient getInstance() {
        return INSTANCE;
    }

    @NonNull
    public String get(@NonNull String url,
                      @NonNull Map<String, String> headers,
                      @NonNull RequestPolicy requestPolicy) throws IOException {
        return request("GET", url, headers, null, requestPolicy).getBody();
    }

    @NonNull
    public NetworkResponse request(@NonNull String method,
                                   @NonNull String url,
                                   @NonNull Map<String, String> headers,
                                   @Nullable byte[] body,
                                   @NonNull RequestPolicy requestPolicy) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt <= requestPolicy.getRetryCount(); attempt++) {
            try {
                return executeRequest(method, url, headers, body, requestPolicy);
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Network request failed for " + url);
    }

    @NonNull
    private NetworkResponse executeRequest(@NonNull String method,
                                           @NonNull String url,
                                           @NonNull Map<String, String> headers,
                                           @Nullable byte[] body,
                                           @NonNull RequestPolicy requestPolicy) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(requestPolicy.getConnectTimeoutMs());
        connection.setReadTimeout(requestPolicy.getReadTimeoutMs());
        connection.setDoInput(true);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
                outputStream.flush();
            }
        }

        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String responseBody = readStream(stream);
        connection.disconnect();

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " for " + url + ": " + responseBody);
        }
        return new NetworkResponse(statusCode, responseBody);
    }

    @NonNull
    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
