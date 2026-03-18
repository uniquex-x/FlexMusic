package com.example.feature_player.player;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.player.ResolvedPlayableSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class StreamingPipeSource implements AutoCloseable {

    private static final String TAG = "StreamingPipeSource";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final ParcelFileDescriptor readSide;
    private final ParcelFileDescriptor writeSide;
    private Thread workerThread;

    private volatile HttpURLConnection connection;
    private volatile boolean closed;

    private StreamingPipeSource(@NonNull ParcelFileDescriptor readSide,
                                @NonNull ParcelFileDescriptor writeSide) {
        this.readSide = readSide;
        this.writeSide = writeSide;
    }

    @NonNull
    static StreamingPipeSource open(@NonNull ResolvedPlayableSource source) throws IOException {
        // This transport is kept as an HTTPS fallback when FFmpeg direct open fails.
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        StreamingPipeSource pipeSource = new StreamingPipeSource(readSide, writeSide);
        pipeSource.workerThread = new Thread(
                () -> pipeSource.streamToPipe(source, writeSide),
                "StreamingPipeSource");
        pipeSource.workerThread.start();
        Log.d(TAG, "open sourceId=" + source.getSourceId() + " url=" + source.getResolvedUrl());
        return pipeSource;
    }

    int detachReadFd() {
        return readSide.detachFd();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        HttpURLConnection localConnection = connection;
        if (localConnection != null) {
            localConnection.disconnect();
        }
        workerThread.interrupt();
        closeQuietly(readSide);
        closeQuietly(writeSide);
    }

    private void streamToPipe(@NonNull ResolvedPlayableSource source,
                              @NonNull ParcelFileDescriptor writeSide) {
        HttpURLConnection localConnection = null;
        try (OutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
            URL url = new URL(source.getResolvedUrl());
            localConnection = (HttpURLConnection) url.openConnection();
            connection = localConnection;
            localConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            localConnection.setReadTimeout(READ_TIMEOUT_MS);
            localConnection.setInstanceFollowRedirects(true);
            localConnection.setRequestMethod("GET");
            if (!source.getUserAgent().isEmpty()) {
                localConnection.setRequestProperty("User-Agent", source.getUserAgent());
            }
            localConnection.connect();
            int responseCode = localConnection.getResponseCode();
            Log.d(TAG, "connected sourceId=" + source.getSourceId() + " code=" + responseCode);

            try (InputStream inputStream = localConnection.getInputStream()) {
                byte[] buffer = new byte[16 * 1024];
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    int readSize = inputStream.read(buffer);
                    if (readSize < 0) {
                        break;
                    }
                    outputStream.write(buffer, 0, readSize);
                    outputStream.flush();
                }
            }
            Log.d(TAG, "stream finished sourceId=" + source.getSourceId());
        } catch (IOException exception) {
            if (!closed) {
                Log.e(TAG, "stream failed sourceId=" + source.getSourceId() + " url=" + source.getResolvedUrl(), exception);
            }
        } finally {
            if (localConnection != null) {
                localConnection.disconnect();
            }
            connection = null;
            closeQuietly(writeSide);
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }
}
