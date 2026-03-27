package com.example.feature_player.coreplayer;

import androidx.annotation.NonNull;

import com.example.core_domain.player.ResolvedPlayableSource;
import com.example.feature_player.player.SoLibraryLoader;

public final class PlayerJNI {

    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_BUFFERING = 5;
    public static final int STATE_COMPLETED = 6;
    public static final int STATE_ERROR = 7;

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
        setDataSource(source, -1, 0L, -1L);
    }

    public void setDataSource(@NonNull ResolvedPlayableSource source,
                              int detachedFd,
                              long fdStartOffset,
                              long fdLength) {
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
                detachedFd,
                fdStartOffset,
                fdLength,
                source.isLiveStream(),
                source.isLocalSource(),
                source.isSeekable(),
                source.getProbeLatencyMs());
    }

    public void prepare() {
        if (nativeHandle != 0L) {
            nativePrepare(nativeHandle);
        }
    }

    public void play() {
        if (nativeHandle != 0L) {
            nativePlay(nativeHandle);
        }
    }

    public void pause() {
        if (nativeHandle != 0L) {
            nativePause(nativeHandle);
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

    public void setVolume(float volume) {
        if (nativeHandle != 0L) {
            nativeSetVolume(nativeHandle, volume);
        }
    }

    public void setPlaybackSpeed(float playbackSpeed) {
        if (nativeHandle != 0L) {
            nativeSetPlaybackSpeed(nativeHandle, playbackSpeed);
        }
    }

    public void setAudioEffectProfile(int audioEffectProfileId) {
        if (nativeHandle != 0L) {
            nativeSetAudioEffectProfile(nativeHandle, audioEffectProfileId);
        }
    }

    public int getState() {
        return nativeHandle == 0L ? STATE_IDLE : nativeGetState(nativeHandle);
    }

    public long getCurrentPosition() {
        return nativeHandle == 0L ? 0L : nativeGetCurrentPosition(nativeHandle);
    }

    public long getDuration() {
        return nativeHandle == 0L ? 0L : nativeGetDuration(nativeHandle);
    }

    @NonNull
    public String getErrorMessage() {
        return nativeHandle == 0L ? "" : nativeGetErrorMessage(nativeHandle);
    }

    public void release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle);
            nativeHandle = 0L;
        }
    }

    private static native long nativeCreate();

    private static native void nativeInitialize(@NonNull String appStoragePath);

    private static native void nativePrepare(long nativeHandle);

    private static native void nativeSetDataSource(long nativeHandle,
                                                   @NonNull String sourceId,
                                                   @NonNull String originalUrl,
                                                   @NonNull String resolvedUrl,
                                                   @NonNull String contentType,
                                                   @NonNull String userAgent,
                                                   int detachedFd,
                                                   long fdStartOffset,
                                                   long fdLength,
                                                   boolean liveStream,
                                                   boolean localSource,
                                                   boolean seekable,
                                                   long probeLatencyMs);

    private static native void nativePlay(long nativeHandle);

    private static native void nativePause(long nativeHandle);

    private static native void nativeSeekTo(long nativeHandle, long positionMs);

    private static native void nativeStop(long nativeHandle);

    private static native void nativeSetVolume(long nativeHandle, float volume);

    private static native void nativeSetPlaybackSpeed(long nativeHandle, float playbackSpeed);

    private static native void nativeSetAudioEffectProfile(long nativeHandle, int audioEffectProfileId);

    private static native void nativeRelease(long nativeHandle);

    private static native boolean nativeIsReady(long nativeHandle);

    private static native int nativeGetState(long nativeHandle);

    private static native long nativeGetCurrentPosition(long nativeHandle);

    private static native long nativeGetDuration(long nativeHandle);

    @NonNull
    private static native String nativeGetErrorMessage(long nativeHandle);
}
