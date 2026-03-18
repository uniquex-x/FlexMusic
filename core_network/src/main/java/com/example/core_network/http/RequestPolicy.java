package com.example.core_network.http;

import androidx.annotation.NonNull;

public class RequestPolicy {

    public static final RequestPolicy DEFAULT = new Builder().build();

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int retryCount;

    private RequestPolicy(@NonNull Builder builder) {
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.retryCount = builder.retryCount;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public static class Builder {
        private int connectTimeoutMs = 8_000;
        private int readTimeoutMs = 10_000;
        private int retryCount = 0;

        @NonNull
        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        @NonNull
        public Builder readTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        @NonNull
        public Builder retryCount(int retryCount) {
            this.retryCount = Math.max(retryCount, 0);
            return this;
        }

        @NonNull
        public RequestPolicy build() {
            return new RequestPolicy(this);
        }
    }
}
