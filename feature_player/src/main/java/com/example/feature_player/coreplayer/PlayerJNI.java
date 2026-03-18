package com.example.feature_player.coreplayer;

import androidx.annotation.NonNull;

import com.example.core_domain.player.ResolvedPlayableSource;
import com.example.feature_player.player.SoLibraryLoader;

public final class PlayerJNI {

    private long nativeHandle;
    private static boolean nativeRuntimeInitialized;

    public PlayerJNI() {
        if (SoLibraryLoader.isLoaded()) {
            nativeHandle = nativeCreate();
        }
    }

    public static synchronized void initializeNativeRuntime(@NonNull String appStoragePath) {
        if (!SoLibraryLoader.isLoaded() || nativeRuntimeInitialized) {
            return;
        }
        nativeInitialize(appStoragePath);
        nativeRuntimeInitialized = true;
    }

    public boolean isReady() {
        return nativeHandle != 0L && nativeIsReady(nativeHandle);
    }

    public void setDataSource(@NonNull ResolvedPlayableSource source) {
        if (nativeHandle == 0L) {
            return;
        }
        nativeSetDataSource(
                nativeHandle,
                source.getSourceId(),
                source.getOriginalUrl(),
                source.getResolvedUrl(),
                source.getContentType(),
                source.getUserAgent(),
                source.isLiveStream(),
                source.isLocalSource(),
                source.isSeekable(),
                source.getProbeLatencyMs());
    }

    public void onPrepared(long durationMs) {
        if (nativeHandle != 0L) {
            nativeOnPrepared(nativeHandle, durationMs);
        }
    }

    public void play() {
        if (nativeHandle != 0L) {
            nativePlay(nativeHandle);
        }
    }

    public void pause(long positionMs) {
        if (nativeHandle != 0L) {
            nativePause(nativeHandle, positionMs);
        }
    }

    public void seekTo(long positionMs) {
        if (nativeHandle != 0L) {
            nativeSeekTo(nativeHandle, positionMs);
        }
    }

    public void stop() {
        if (nativeHandle != 0L) {
            nativeStop(nativeHandle);
        }
    }

    public void onCompletion(long durationMs) {
        if (nativeHandle != 0L) {
            nativeOnCompletion(nativeHandle, durationMs);
        }
    }

    public void release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle);
            nativeHandle = 0L;
        }
    }

    private static native long nativeCreate();

    private static native void nativeInitialize(@NonNull String appStoragePath);

    private static native void nativeSetDataSource(long nativeHandle,
                                                   @NonNull String sourceId,
                                                   @NonNull String originalUrl,
                                                   @NonNull String resolvedUrl,
                                                   @NonNull String contentType,
                                                   @NonNull String userAgent,
                                                   boolean liveStream,
                                                   boolean localSource,
                                                   boolean seekable,
                                                   long probeLatencyMs);

    private static native void nativeOnPrepared(long nativeHandle, long durationMs);

    private static native void nativePlay(long nativeHandle);

    private static native void nativePause(long nativeHandle, long positionMs);

    private static native void nativeSeekTo(long nativeHandle, long positionMs);

    private static native void nativeStop(long nativeHandle);

    private static native void nativeOnCompletion(long nativeHandle, long durationMs);

    private static native void nativeRelease(long nativeHandle);

    private static native boolean nativeIsReady(long nativeHandle);
}
