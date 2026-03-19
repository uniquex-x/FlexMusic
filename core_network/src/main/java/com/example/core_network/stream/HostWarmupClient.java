package com.example.core_network.stream;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class HostWarmupClient {

    private static final int CONNECT_TIMEOUT_MS = 2_000;

    long warmup(@NonNull String url) throws IOException {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("Warmup host missing for " + url);
        }

        int port = uri.getPort();
        if (port <= 0) {
            port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }

        long startAt = SystemClock.elapsedRealtime();
        if ("https".equalsIgnoreCase(scheme)) {
            SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket();
            try {
                sslSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                sslSocket.setSoTimeout(CONNECT_TIMEOUT_MS);
                sslSocket.startHandshake();
            } finally {
                try {
                    sslSocket.close();
                } catch (IOException ignored) {
                }
            }
            return SystemClock.elapsedRealtime() - startAt;
        }

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        return SystemClock.elapsedRealtime() - startAt;
    }
}
