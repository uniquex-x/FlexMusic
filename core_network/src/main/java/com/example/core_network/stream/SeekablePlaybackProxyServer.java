package com.example.core_network.stream;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SeekablePlaybackProxyServer {

    private static final String TAG = "SeekableProxy";
    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int SOCKET_BACKLOG = 8;
    private static final long SESSION_TTL_MS = 10 * 60 * 1000L;

    private static final SeekablePlaybackProxyServer INSTANCE = new SeekablePlaybackProxyServer();

    private final ConcurrentMap<String, ProxySession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService requestExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService cleanerExecutor = Executors.newSingleThreadScheduledExecutor();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private int port = -1;

    private SeekablePlaybackProxyServer() {
        cleanerExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    @NonNull
    public static SeekablePlaybackProxyServer getInstance() {
        return INSTANCE;
    }

    @NonNull
    public synchronized ProxySessionHandle openSession(@NonNull String sourceId,
                                                       @NonNull String remoteUrl,
                                                       @NonNull String userAgent) throws IOException {
        ensureStartedLocked();
        String token = UUID.randomUUID().toString();
        ProxySession session = new ProxySession(token, sourceId, remoteUrl, userAgent);
        sessions.put(token, session);
        String localUrl = "http://127.0.0.1:" + port + "/stream/" + token;
        Log.d(TAG, "session open sourceId=" + sourceId + " remoteUrl=" + remoteUrl + " localUrl=" + localUrl);
        return new ProxySessionHandle(token, localUrl);
    }

    public void releaseSession(@NonNull String token) {
        ProxySession session = sessions.remove(token);
        if (session != null) {
            session.close();
        }
    }

    private synchronized void ensureStartedLocked() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        serverSocket = new ServerSocket(0, SOCKET_BACKLOG, InetAddress.getByName("127.0.0.1"));
        port = serverSocket.getLocalPort();
        acceptThread = new Thread(this::acceptLoop, "SeekablePlaybackProxy");
        acceptThread.start();
        Log.d(TAG, "server started port=" + port);
    }

    private void acceptLoop() {
        while (true) {
            ServerSocket localServerSocket;
            synchronized (this) {
                localServerSocket = serverSocket;
            }
            if (localServerSocket == null || localServerSocket.isClosed()) {
                return;
            }

            try {
                Socket clientSocket = localServerSocket.accept();
                requestExecutor.execute(() -> handleClient(clientSocket));
            } catch (SocketException socketException) {
                Log.w(TAG, "accept loop stopped", socketException);
                return;
            } catch (IOException ioException) {
                Log.e(TAG, "accept failed", ioException);
            }
        }
    }

    private void handleClient(@NonNull Socket clientSocket) {
        try (Socket socket = clientSocket) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
            HttpRequest request = HttpRequest.parse(inputStream);
            if (request == null) {
                return;
            }

            String token = resolveToken(request.path);
            ProxySession session = token == null ? null : sessions.get(token);
            if (session == null) {
                writeSimpleResponse(outputStream, "404 Not Found", "Session not found");
                outputStream.flush();
                return;
            }

            session.serve(request, outputStream);
            outputStream.flush();
        } catch (IOException ioException) {
            Log.e(TAG, "handle client failed", ioException);
        }
    }

    @Nullable
    private String resolveToken(@Nullable String path) {
        if (path == null) {
            return null;
        }
        final String prefix = "/stream/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        return path.substring(prefix.length());
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ProxySession> entry : sessions.entrySet()) {
            ProxySession session = entry.getValue();
            if (now - session.lastAccessAtMs() < SESSION_TTL_MS) {
                continue;
            }
            if (sessions.remove(entry.getKey(), session)) {
                session.close();
                Log.d(TAG, "session expired sourceId=" + session.sourceId);
            }
        }
    }

    private static void writeSimpleResponse(@NonNull OutputStream outputStream,
                                            @NonNull String status,
                                            @NonNull String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        outputStream.write(headers.getBytes(StandardCharsets.US_ASCII));
        outputStream.write(body);
    }

    public static final class ProxySessionHandle implements AutoCloseable {
        private final String token;
        private final String localUrl;

        private ProxySessionHandle(@NonNull String token, @NonNull String localUrl) {
            this.token = token;
            this.localUrl = localUrl;
        }

        @NonNull
        public String getLocalUrl() {
            return localUrl;
        }

        @Override
        public void close() {
            SeekablePlaybackProxyServer.getInstance().releaseSession(token);
        }
    }

    private static final class ProxySession {
        private final String token;
        private final String sourceId;
        private final String remoteUrl;
        private final String userAgent;

        private volatile String resolvedUpstreamUrl;
        private volatile String contentType = "audio/mpeg";
        private volatile long contentLength = -1L;
        private volatile byte[] headBytes = new byte[0];
        private volatile long lastAccessAtMs = System.currentTimeMillis();

        private ProxySession(@NonNull String token,
                             @NonNull String sourceId,
                             @NonNull String remoteUrl,
                             @NonNull String userAgent) {
            this.token = token;
            this.sourceId = sourceId;
            this.remoteUrl = remoteUrl;
            this.userAgent = userAgent;
            this.resolvedUpstreamUrl = remoteUrl;
        }

        private void serve(@NonNull HttpRequest request,
                           @NonNull OutputStream outputStream) throws IOException {
            lastAccessAtMs = System.currentTimeMillis();

            RangeRequest rangeRequest = RangeRequest.parse(request.headers.get("range"), contentLength);
            boolean partialResponse = rangeRequest.isPartial && contentLength > 0;
            long responseStart = rangeRequest.start;
            long responseEnd = rangeRequest.end;

            HttpURLConnection upstreamConnection = null;
            InputStream upstreamStream = null;
            try {
                Long upstreamEnd = partialResponse && responseEnd < Long.MAX_VALUE
                        ? responseEnd
                        : null;
                upstreamConnection = openConnection(
                        resolvedUpstreamUrl,
                        userAgent,
                        responseStart,
                        upstreamEnd);
                int responseCode = upstreamConnection.getResponseCode();
                updateMetadataFromConnection(upstreamConnection, responseCode);
                rangeRequest = RangeRequest.parse(request.headers.get("range"), contentLength);
                partialResponse = rangeRequest.isPartial && contentLength > 0;
                responseStart = rangeRequest.start;
                responseEnd = rangeRequest.end;
                long responseLength = resolveResponseLength(responseStart, responseEnd, contentLength, partialResponse);
                writeHeaders(outputStream, request.method, partialResponse, responseStart, responseEnd, responseLength);
                if ("HEAD".equals(request.method)) {
                    return;
                }

                upstreamStream = new BufferedInputStream(upstreamConnection.getInputStream());
                long nextOffset = responseStart;
                if (headBytes.length > 0 && responseStart < headBytes.length) {
                    int cachedStart = (int) responseStart;
                    int cachedEnd = (int) Math.min(
                            headBytes.length,
                            partialResponse ? responseEnd + 1 : headBytes.length);
                    outputStream.write(headBytes, cachedStart, cachedEnd - cachedStart);
                    nextOffset = cachedEnd;
                }
                if (responseCode == HttpURLConnection.HTTP_OK && nextOffset > 0) {
                    skipFully(upstreamStream, nextOffset);
                }

                byte[] buffer = new byte[16 * 1024];
                long remaining = partialResponse
                        ? (responseEnd - nextOffset + 1)
                        : Long.MAX_VALUE;
                while (remaining > 0) {
                    int readSize = upstreamStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (readSize < 0) {
                        break;
                    }
                    outputStream.write(buffer, 0, readSize);
                    remaining -= readSize;
                }
            } finally {
                if (upstreamStream != null) {
                    try {
                        upstreamStream.close();
                    } catch (IOException ignored) {
                    }
                }
                if (upstreamConnection != null) {
                    upstreamConnection.disconnect();
                }
            }
        }

        private void writeHeaders(@NonNull OutputStream outputStream,
                                  @NonNull String method,
                                  boolean partialResponse,
                                  long responseStart,
                                  long responseEnd,
                                  long responseLength) throws IOException {
            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ")
                    .append(partialResponse ? "206 Partial Content" : "200 OK")
                    .append("\r\n");
            headers.append("Content-Type: ").append(contentType).append("\r\n");
            headers.append("Accept-Ranges: bytes\r\n");
            if (contentLength > 0) {
                if (partialResponse) {
                    headers.append("Content-Range: bytes ")
                            .append(responseStart)
                            .append("-")
                            .append(responseEnd)
                            .append("/")
                            .append(contentLength)
                            .append("\r\n");
                }
                if (responseLength >= 0) {
                    headers.append("Content-Length: ").append(responseLength).append("\r\n");
                }
            }
            headers.append("Connection: close\r\n\r\n");
            outputStream.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
            Log.d(TAG, "serve sourceId=" + sourceId
                    + " token=" + token
                    + " method=" + method
                    + " partial=" + partialResponse
                    + " start=" + responseStart
                    + " end=" + responseEnd
                    + " contentLength=" + contentLength);
        }

        private long resolveResponseLength(long responseStart,
                                           long responseEnd,
                                           long totalLength,
                                           boolean partialResponse) {
            if (partialResponse) {
                return responseEnd - responseStart + 1;
            }
            if (totalLength <= 0) {
                return -1L;
            }
            return totalLength - responseStart;
        }

        private long lastAccessAtMs() {
            return lastAccessAtMs;
        }

        private void close() {
            headBytes = new byte[0];
        }

        private void updateMetadataFromConnection(@NonNull HttpURLConnection connection,
                                                  int responseCode) {
            resolvedUpstreamUrl = connection.getURL() != null
                    ? connection.getURL().toString()
                    : resolvedUpstreamUrl;
            String upstreamContentType = connection.getContentType();
            if (upstreamContentType != null && !upstreamContentType.isEmpty()) {
                contentType = upstreamContentType;
            }
            long upstreamContentLength = resolveContentLength(connection, responseCode);
            if (upstreamContentLength > 0) {
                contentLength = upstreamContentLength;
            }
        }

        @NonNull
        private static HttpURLConnection openConnection(@NonNull String url,
                                                        @NonNull String userAgent,
                                                        long startOffset,
                                                        @Nullable Long endOffset) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Icy-MetaData", "1");
            if (!userAgent.isEmpty()) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
            if (startOffset > 0 || endOffset != null) {
                StringBuilder rangeHeader = new StringBuilder("bytes=").append(startOffset).append("-");
                if (endOffset != null) {
                    rangeHeader.append(endOffset);
                }
                connection.setRequestProperty("Range", rangeHeader.toString());
            }
            return connection;
        }

        private static long resolveContentLength(@NonNull HttpURLConnection connection, int responseCode) {
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                int slashIndex = contentRange.lastIndexOf('/');
                if (slashIndex >= 0 && slashIndex + 1 < contentRange.length()) {
                    try {
                        return Long.parseLong(contentRange.substring(slashIndex + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0 && responseCode == HttpURLConnection.HTTP_OK) {
                return contentLength;
            }
            return contentLength > 0 ? contentLength : -1L;
        }

        private static void skipFully(@NonNull InputStream inputStream, long bytesToSkip) throws IOException {
            long remaining = bytesToSkip;
            while (remaining > 0) {
                long skipped = inputStream.skip(remaining);
                if (skipped > 0) {
                    remaining -= skipped;
                    continue;
                }
                if (inputStream.read() < 0) {
                    break;
                }
                remaining--;
            }
        }
    }

    private static final class RangeRequest {
        private final boolean isPartial;
        private final long start;
        private final long end;

        private RangeRequest(boolean isPartial, long start, long end) {
            this.isPartial = isPartial;
            this.start = start;
            this.end = end;
        }

        @NonNull
        private static RangeRequest parse(@Nullable String headerValue, long totalLength) {
            if (headerValue == null) {
                return new RangeRequest(false, 0L, Math.max(totalLength - 1L, 0L));
            }
            String normalized = headerValue.trim().toLowerCase(Locale.US);
            if (!normalized.startsWith("bytes=")) {
                return new RangeRequest(false, 0L, Math.max(totalLength - 1L, 0L));
            }
            String range = normalized.substring("bytes=".length());
            int dashIndex = range.indexOf('-');
            if (dashIndex <= 0) {
                return new RangeRequest(false, 0L, Math.max(totalLength - 1L, 0L));
            }
            try {
                long start = Long.parseLong(range.substring(0, dashIndex));
                long end = totalLength > 0 ? totalLength - 1L : Long.MAX_VALUE;
                if (dashIndex + 1 < range.length()) {
                    end = Long.parseLong(range.substring(dashIndex + 1));
                }
                if (totalLength > 0) {
                    end = Math.min(end, totalLength - 1L);
                }
                return new RangeRequest(true, Math.max(start, 0L), Math.max(end, start));
            } catch (NumberFormatException numberFormatException) {
                return new RangeRequest(false, 0L, Math.max(totalLength - 1L, 0L));
            }
        }
    }

    private static final class HttpRequest {
        private final String method;
        private final String path;
        private final Map<String, String> headers;

        private HttpRequest(@NonNull String method,
                            @NonNull String path,
                            @NonNull Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }

        @Nullable
        private static HttpRequest parse(@NonNull InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                return null;
            }

            Map<String, String> headers = new HashMap<>();
            while (true) {
                String headerLine = reader.readLine();
                if (headerLine == null || headerLine.isEmpty()) {
                    break;
                }
                int separatorIndex = headerLine.indexOf(':');
                if (separatorIndex <= 0) {
                    continue;
                }
                String name = headerLine.substring(0, separatorIndex).trim().toLowerCase(Locale.US);
                String value = headerLine.substring(separatorIndex + 1).trim();
                headers.put(name, value);
            }
            return new HttpRequest(requestParts[0], requestParts[1], headers);
        }
    }
}
