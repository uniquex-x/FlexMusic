package com.example.core_network.http;

import androidx.annotation.NonNull;

public class NetworkResponse {

    private final int statusCode;
    private final String body;

    public NetworkResponse(int statusCode, @NonNull String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @NonNull
    public String getBody() {
        return body;
    }
}
