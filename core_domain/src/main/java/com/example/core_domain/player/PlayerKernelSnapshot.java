package com.example.core_domain.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PlayerKernelSnapshot {

    private final PlayerKernelState state;
    private final long currentPositionMs;
    private final long durationMs;
    private final boolean seekable;
    private final boolean nativeReady;
    @Nullable
    private final ResolvedPlayableSource currentSource;
    @Nullable
    private final String errorMessage;

    public PlayerKernelSnapshot(@NonNull PlayerKernelState state,
                                long currentPositionMs,
                                long durationMs,
                                boolean seekable,
                                boolean nativeReady,
                                @Nullable ResolvedPlayableSource currentSource,
                                @Nullable String errorMessage) {
        this.state = state;
        this.currentPositionMs = currentPositionMs;
        this.durationMs = durationMs;
        this.seekable = seekable;
        this.nativeReady = nativeReady;
        this.currentSource = currentSource;
        this.errorMessage = errorMessage;
    }

    @NonNull
    public static PlayerKernelSnapshot idle() {
        return new PlayerKernelSnapshot(PlayerKernelState.IDLE, 0L, 0L, false, false, null, null);
    }

    @NonNull
    public PlayerKernelState getState() {
        return state;
    }

    public long getCurrentPositionMs() {
        return currentPositionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSeekable() {
        return seekable;
    }

    public boolean isNativeReady() {
        return nativeReady;
    }

    @Nullable
    public ResolvedPlayableSource getCurrentSource() {
        return currentSource;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
}
