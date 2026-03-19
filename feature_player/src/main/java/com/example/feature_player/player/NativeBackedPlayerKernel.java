package com.example.feature_player.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlayerKernel;
import com.example.core_domain.player.PlayerKernelListener;
import com.example.core_domain.player.PlayerKernelSnapshot;
import com.example.core_domain.player.PlayerKernelState;
import com.example.core_domain.player.ResolvedPlayableSource;
import com.example.feature_player.coreplayer.PlayerJNI;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class NativeBackedPlayerKernel implements PlayerKernel {

    private static final String TAG = "NativePlayerKernel";
    private static final long POLL_INTERVAL_MS = 200L;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<PlayerKernelListener> listeners = new LinkedHashSet<>();
    private final PlayerJNI playerJni = new PlayerJNI();
    private final Runnable snapshotPoller = new Runnable() {
        @Override
        public void run() {
            synchronized (NativeBackedPlayerKernel.this) {
                if (released) {
                    return;
                }
                refreshSnapshotLocked(true);
                if (shouldContinuePollingLocked()) {
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS);
                } else {
                    polling = false;
                }
            }
        }
    };

    private PlayerKernelSnapshot snapshot = PlayerKernelSnapshot.idle();
    private ResolvedPlayableSource requestedSource;
    private ResolvedPlayableSource currentSource;
    private float volume = 1f;
    private boolean released;
    private boolean polling;
    private boolean currentHttpsUsingPipeFallback;
    private boolean httpsPipeFallbackAttempted;
    private StreamingPipeSource activeStreamingPipeSource;

    NativeBackedPlayerKernel(@NonNull Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public synchronized void addListener(@NonNull PlayerKernelListener listener) {
        listeners.add(listener);
        notifyListener(listener, snapshot);
    }

    @Override
    public synchronized void removeListener(@NonNull PlayerKernelListener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized void prepare(@NonNull ResolvedPlayableSource source) throws IOException {
        ensureNotReleased();
        requestedSource = source;
        httpsPipeFallbackAttempted = false;
        prepareWithTransportLocked(source, false);
    }

    private void prepareWithTransportLocked(@NonNull ResolvedPlayableSource source,
                                            boolean useHttpsPipeFallback) throws IOException {
        closeActiveStreamingPipeSourceLocked();
        NativeSourceDescriptor nativeSourceDescriptor = createNativeSourceDescriptor(source, useHttpsPipeFallback);
        ResolvedPlayableSource nativePlaybackSource = nativeSourceDescriptor.forceNonSeekable
                ? new ResolvedPlayableSource(
                source.getSourceId(),
                source.getOriginalUrl(),
                source.getResolvedUrl(),
                source.getContentType(),
                source.getUserAgent(),
                source.isLiveStream(),
                source.isLocalSource(),
                false,
                source.getProbeLatencyMs())
                : source;
        currentSource = nativePlaybackSource;
        currentHttpsUsingPipeFallback = nativeSourceDescriptor.httpsPipeFallback;
        Log.d(TAG, "prepare sourceId=" + nativePlaybackSource.getSourceId()
                + " originalUrl=" + nativePlaybackSource.getOriginalUrl()
                + " url=" + nativePlaybackSource.getResolvedUrl()
                + " transport=" + nativeSourceDescriptor.transportName
                + " local=" + nativePlaybackSource.isLocalSource()
                + " fd=" + nativeSourceDescriptor.detachedFd);
        playerJni.setDataSource(
                nativePlaybackSource,
                nativeSourceDescriptor.detachedFd,
                nativeSourceDescriptor.startOffset,
                nativeSourceDescriptor.length);
        playerJni.setVolume(volume);
        updateSnapshotLocked(
                PlayerKernelState.PREPARING,
                0L,
                0L,
                nativePlaybackSource.isSeekable(),
                playerJni.isReady(),
                currentSource,
                null,
                true);
        playerJni.prepare();
        schedulePollingLocked();
    }

    @Override
    public synchronized void play() {
        if (released) {
            return;
        }
        playerJni.play();
        schedulePollingLocked();
        refreshSnapshotLocked(true);
    }

    @Override
    public synchronized void pause() {
        if (released) {
            return;
        }
        playerJni.pause();
        refreshSnapshotLocked(true);
    }

    @Override
    public synchronized void stop() {
        if (released) {
            return;
        }
        closeActiveStreamingPipeSourceLocked();
        playerJni.stop();
        requestedSource = null;
        currentSource = null;
        currentHttpsUsingPipeFallback = false;
        httpsPipeFallbackAttempted = false;
        stopPollingLocked();
        updateSnapshotLocked(PlayerKernelState.IDLE, 0L, 0L, false, playerJni.isReady(), null, null, true);
    }

    @Override
    public synchronized void seekTo(long positionMs) {
        if (released || currentSource == null || !currentSource.isSeekable()) {
            return;
        }
        long safePositionMs = Math.max(0L, Math.min(positionMs, Math.max(snapshot.getDurationMs(), positionMs)));
        playerJni.seekTo(safePositionMs);
        updateSnapshotLocked(
                PlayerKernelState.PREPARING,
                safePositionMs,
                Math.max(snapshot.getDurationMs(), safePositionMs),
                true,
                playerJni.isReady(),
                currentSource,
                null,
                true);
        schedulePollingLocked();
    }

    @Override
    public synchronized void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (!released) {
            playerJni.setVolume(this.volume);
        }
    }

    @Override
    public synchronized long getCurrentPosition() {
        return released ? snapshot.getCurrentPositionMs() : playerJni.getCurrentPosition();
    }

    @Override
    public synchronized long getDuration() {
        return released ? snapshot.getDurationMs() : playerJni.getDuration();
    }

    @Override
    public synchronized boolean isPlaying() {
        PlayerKernelState state = snapshot.getState();
        return state == PlayerKernelState.PLAYING || state == PlayerKernelState.BUFFERING;
    }

    @Override
    public synchronized boolean isNativeReady() {
        return !released && playerJni.isReady();
    }

    @NonNull
    @Override
    public synchronized PlayerKernelSnapshot getSnapshot() {
        refreshSnapshotLocked(false);
        return snapshot;
    }

    @Override
    public synchronized void release() {
        if (released) {
            return;
        }
        released = true;
        closeActiveStreamingPipeSourceLocked();
        stopPollingLocked();
        listeners.clear();
        requestedSource = null;
        currentSource = null;
        currentHttpsUsingPipeFallback = false;
        httpsPipeFallbackAttempted = false;
        playerJni.release();
        snapshot = PlayerKernelSnapshot.idle();
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException("Player kernel has been released");
        }
    }

    @NonNull
    private NativeSourceDescriptor createNativeSourceDescriptor(@NonNull ResolvedPlayableSource source,
                                                                boolean useHttpsPipeFallback) throws IOException {
        Uri uri = Uri.parse(source.getResolvedUrl());
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            if (useHttpsPipeFallback) {
                // Keep the Java pipe path as a single-shot fallback when the native HTTPS
                // transport fails during prepare.
                activeStreamingPipeSource = StreamingPipeSource.open(source);
                Log.w(TAG, "fallback to https pipe sourceId=" + source.getSourceId()
                        + " url=" + source.getResolvedUrl());
                return new NativeSourceDescriptor(
                        activeStreamingPipeSource.detachReadFd(),
                        0L,
                        -1L,
                        true,
                        true,
                        "pipe_fd");
            }
            Log.d(TAG, "prefer ffmpeg network sourceId=" + source.getSourceId()
                    + " url=" + source.getResolvedUrl());
            return new NativeSourceDescriptor(-1, 0L, -1L, false, false, "ffmpeg");
        }
        if ("http".equalsIgnoreCase(scheme)) {
            Log.d(TAG, "prefer ffmpeg network sourceId=" + source.getSourceId()
                    + " url=" + source.getResolvedUrl());
            return new NativeSourceDescriptor(-1, 0L, -1L, false, false, "ffmpeg");
        }
        if (!"content".equalsIgnoreCase(scheme) && !"android.resource".equalsIgnoreCase(scheme)) {
            return NativeSourceDescriptor.none();
        }
        try (AssetFileDescriptor assetFileDescriptor =
                     appContext.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (assetFileDescriptor == null || assetFileDescriptor.getParcelFileDescriptor() == null) {
                throw new IOException("Open local asset descriptor failed for " + source.getResolvedUrl());
            }
            int detachedFd = assetFileDescriptor.getParcelFileDescriptor().detachFd();
            return new NativeSourceDescriptor(
                    detachedFd,
                    assetFileDescriptor.getStartOffset(),
                    assetFileDescriptor.getLength(),
                    false,
                    false,
                    "fd");
        } catch (RuntimeException runtimeException) {
            throw new IOException("Detach local audio fd failed for " + source.getResolvedUrl(), runtimeException);
        }
    }

    private void closeActiveStreamingPipeSourceLocked() {
        if (activeStreamingPipeSource != null) {
            activeStreamingPipeSource.close();
            activeStreamingPipeSource = null;
        }
    }

    private void refreshSnapshotLocked(boolean dispatch) {
        if (released) {
            return;
        }
        PlayerKernelState nextState = mapState(playerJni.getState());
        long currentPositionMs = playerJni.getCurrentPosition();
        long durationMs = playerJni.getDuration();
        String errorMessage = playerJni.getErrorMessage();
        if (shouldRetryHttpsWithPipeLocked(nextState, errorMessage)) {
            retryHttpsWithPipeLocked(errorMessage);
            return;
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Log.w(TAG, "native error sourceId="
                    + (currentSource != null ? currentSource.getSourceId() : "null")
                    + " error=" + errorMessage);
        }
        updateSnapshotLocked(
                nextState,
                currentPositionMs,
                durationMs,
                currentSource != null && currentSource.isSeekable(),
                playerJni.isReady(),
                currentSource,
                errorMessage.isEmpty() ? null : errorMessage,
                dispatch);
    }

    private boolean shouldRetryHttpsWithPipeLocked(@NonNull PlayerKernelState nextState,
                                                   String errorMessage) {
        if (requestedSource == null || !isHttpsSource(requestedSource)) {
            return false;
        }
        if (currentHttpsUsingPipeFallback || httpsPipeFallbackAttempted) {
            return false;
        }
        if (snapshot.getState() != PlayerKernelState.PREPARING || nextState != PlayerKernelState.ERROR) {
            return false;
        }
        Log.w(TAG, "ffmpeg https prepare failed, scheduling pipe fallback sourceId="
                + requestedSource.getSourceId()
                + " error=" + errorMessage);
        return true;
    }

    private void retryHttpsWithPipeLocked(String errorMessage) {
        if (requestedSource == null) {
            return;
        }
        httpsPipeFallbackAttempted = true;
        try {
            prepareWithTransportLocked(requestedSource, true);
        } catch (IOException fallbackError) {
            String combinedError = "FFmpeg HTTPS failed: " + errorMessage
                    + "; pipe fallback failed: " + fallbackError.getMessage();
            Log.e(TAG, "https pipe fallback failed sourceId=" + requestedSource.getSourceId()
                    + " url=" + requestedSource.getResolvedUrl()
                    + " initialError=" + errorMessage, fallbackError);
            updateSnapshotLocked(
                    PlayerKernelState.ERROR,
                    0L,
                    0L,
                    false,
                    playerJni.isReady(),
                    currentSource,
                    combinedError,
                    true);
        }
    }

    private boolean isHttpsSource(@NonNull ResolvedPlayableSource source) {
        return "https".equalsIgnoreCase(Uri.parse(source.getResolvedUrl()).getScheme());
    }

    private void updateSnapshotLocked(@NonNull PlayerKernelState state,
                                      long currentPositionMs,
                                      long durationMs,
                                      boolean seekable,
                                      boolean nativeReady,
                                      ResolvedPlayableSource source,
                                      String errorMessage,
                                      boolean dispatch) {
        PlayerKernelSnapshot nextSnapshot = new PlayerKernelSnapshot(
                state,
                Math.max(0L, currentPositionMs),
                Math.max(0L, durationMs),
                seekable,
                nativeReady,
                source,
                errorMessage);
        boolean changed = !sameSnapshot(snapshot, nextSnapshot);
        snapshot = nextSnapshot;
        if (dispatch && changed) {
            dispatchSnapshot(nextSnapshot);
        }
    }

    private boolean sameSnapshot(@NonNull PlayerKernelSnapshot left, @NonNull PlayerKernelSnapshot right) {
        return left.getState() == right.getState()
                && left.getCurrentPositionMs() == right.getCurrentPositionMs()
                && left.getDurationMs() == right.getDurationMs()
                && left.isSeekable() == right.isSeekable()
                && left.isNativeReady() == right.isNativeReady()
                && Objects.equals(left.getCurrentSource(), right.getCurrentSource())
                && Objects.equals(left.getErrorMessage(), right.getErrorMessage());
    }

    private void dispatchSnapshot(@NonNull PlayerKernelSnapshot snapshot) {
        for (PlayerKernelListener listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(@NonNull PlayerKernelListener listener,
                                @NonNull PlayerKernelSnapshot snapshot) {
        mainHandler.post(() -> listener.onSnapshotChanged(snapshot));
    }

    private void schedulePollingLocked() {
        if (polling) {
            return;
        }
        polling = true;
        mainHandler.post(snapshotPoller);
    }

    private void stopPollingLocked() {
        polling = false;
        mainHandler.removeCallbacks(snapshotPoller);
    }

    private boolean shouldContinuePollingLocked() {
        PlayerKernelState state = snapshot.getState();
        return currentSource != null
                && (state == PlayerKernelState.PREPARING
                || state == PlayerKernelState.READY
                || state == PlayerKernelState.PLAYING
                || state == PlayerKernelState.BUFFERING
                || state == PlayerKernelState.PAUSED);
    }

    @NonNull
    private PlayerKernelState mapState(int nativeState) {
        switch (nativeState) {
            case PlayerJNI.STATE_PREPARING:
                return PlayerKernelState.PREPARING;
            case PlayerJNI.STATE_READY:
                return PlayerKernelState.READY;
            case PlayerJNI.STATE_PLAYING:
                return PlayerKernelState.PLAYING;
            case PlayerJNI.STATE_PAUSED:
                return PlayerKernelState.PAUSED;
            case PlayerJNI.STATE_BUFFERING:
                return PlayerKernelState.BUFFERING;
            case PlayerJNI.STATE_COMPLETED:
                return PlayerKernelState.COMPLETED;
            case PlayerJNI.STATE_ERROR:
                return PlayerKernelState.ERROR;
            case PlayerJNI.STATE_IDLE:
            default:
                return PlayerKernelState.IDLE;
        }
    }

    private static final class NativeSourceDescriptor {
        private final int detachedFd;
        private final long startOffset;
        private final long length;
        private final boolean forceNonSeekable;
        private final boolean httpsPipeFallback;
        private final String transportName;

        private NativeSourceDescriptor(int detachedFd,
                                       long startOffset,
                                       long length,
                                       boolean forceNonSeekable,
                                       boolean httpsPipeFallback,
                                       @NonNull String transportName) {
            this.detachedFd = detachedFd;
            this.startOffset = startOffset;
            this.length = length;
            this.forceNonSeekable = forceNonSeekable;
            this.httpsPipeFallback = httpsPipeFallback;
            this.transportName = transportName;
        }

        @NonNull
        private static NativeSourceDescriptor none() {
            return new NativeSourceDescriptor(-1, 0L, -1L, false, false, "direct");
        }
    }
}
