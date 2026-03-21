package com.example.core_domain.player;

import androidx.annotation.NonNull;

import java.io.IOException;

public interface PlayerKernel {

    void addListener(@NonNull PlayerKernelListener listener);

    void removeListener(@NonNull PlayerKernelListener listener);

    void prepare(@NonNull ResolvedPlayableSource source) throws IOException;

    void play();

    void pause();

    void stop();

    void seekTo(long positionMs);

    void setVolume(float volume);

    void setPlaybackSpeed(float playbackSpeed);

    long getCurrentPosition();

    long getDuration();

    boolean isPlaying();

    boolean isNativeReady();

    @NonNull
    PlayerKernelSnapshot getSnapshot();

    void release();
}
