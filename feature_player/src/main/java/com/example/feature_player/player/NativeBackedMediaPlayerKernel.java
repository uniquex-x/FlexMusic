package com.example.feature_player.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlayerKernel;
import com.example.core_domain.player.PlayerKernelListener;
import com.example.core_domain.player.PlayerKernelSnapshot;
import com.example.core_domain.player.PlayerKernelState;
import com.example.core_domain.player.ResolvedPlayableSource;
import com.example.feature_player.coreplayer.PlayerJNI;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class NativeBackedMediaPlayerKernel implements PlayerKernel {

    private final Context appContext;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Set<PlayerKernelListener> listeners = new LinkedHashSet<>();
    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private final PlayerJNI playerJni = new PlayerJNI();

    private PlayerKernelSnapshot snapshot = PlayerKernelSnapshot.idle();
    private ResolvedPlayableSource currentSource;
    private PlayerKernelState lastSteadyState = PlayerKernelState.IDLE;
    private boolean prepared;
    private float volume = 1f;

    NativeBackedMediaPlayerKernel(@NonNull Context appContext) {
        this.appContext = appContext;
        configureMediaPlayer();
    }

    private void configureMediaPlayer() {
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setOnPreparedListener(mp -> {
            synchronized (NativeBackedMediaPlayerKernel.this) {
                prepared = true;
                playerJni.onPrepared(safeGetDurationLocked());
                updateSnapshotLocked(PlayerKernelState.READY, 0L, safeGetDurationLocked(), null);
                startPlaybackLocked();
            }
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            synchronized (NativeBackedMediaPlayerKernel.this) {
                long duration = safeGetDurationLocked();
                playerJni.onCompletion(duration);
                prepared = true;
                lastSteadyState = PlayerKernelState.COMPLETED;
                updateSnapshotLocked(PlayerKernelState.COMPLETED, duration, duration, null);
            }
        });
        mediaPlayer.setOnInfoListener((mp, what, extra) -> {
            synchronized (NativeBackedMediaPlayerKernel.this) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    updateSnapshotLocked(PlayerKernelState.BUFFERING, safeGetCurrentPositionLocked(), safeGetDurationLocked(), null);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    PlayerKernelState nextState = mp.isPlaying() ? PlayerKernelState.PLAYING : lastSteadyState;
                    updateSnapshotLocked(nextState, safeGetCurrentPositionLocked(), safeGetDurationLocked(), null);
                }
            }
            return false;
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            synchronized (NativeBackedMediaPlayerKernel.this) {
                prepared = false;
                updateSnapshotLocked(PlayerKernelState.ERROR, safeGetCurrentPositionLocked(), safeGetDurationLocked(),
                        "MediaPlayer error " + what + "/" + extra);
            }
            return true;
        });
    }

    @Override
    public synchronized void addListener(@NonNull PlayerKernelListener listener) {
        listeners.add(listener);
        notifyListener(listener, getSnapshot());
    }

    @Override
    public synchronized void removeListener(@NonNull PlayerKernelListener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized void prepare(@NonNull ResolvedPlayableSource source) throws IOException {
        currentSource = source;
        prepared = false;
        lastSteadyState = PlayerKernelState.PREPARING;
        playerJni.setDataSource(source);
        mediaPlayer.reset();
        setMediaPlayerDataSource(source);
        updateSnapshotLocked(PlayerKernelState.PREPARING, 0L, 0L, null);
        mediaPlayer.prepareAsync();
    }

    @Override
    public synchronized void play() {
        if (!prepared) {
            return;
        }
        if (snapshot.getState() == PlayerKernelState.COMPLETED) {
            mediaPlayer.seekTo(0);
            playerJni.seekTo(0L);
        }
        startPlaybackLocked();
    }

    @Override
    public synchronized void pause() {
        if (!prepared) {
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
            long position = safeGetCurrentPositionLocked();
            playerJni.pause(position);
            lastSteadyState = PlayerKernelState.PAUSED;
            updateSnapshotLocked(PlayerKernelState.PAUSED, position, safeGetDurationLocked(), null);
        } catch (IllegalStateException ignored) {
            updateSnapshotLocked(PlayerKernelState.ERROR, 0L, 0L, "Pause failed");
        }
    }

    @Override
    public synchronized void stop() {
        prepared = false;
        currentSource = null;
        try {
            mediaPlayer.reset();
        } catch (IllegalStateException ignored) {
        }
        playerJni.stop();
        lastSteadyState = PlayerKernelState.IDLE;
        updateSnapshotLocked(PlayerKernelState.IDLE, 0L, 0L, null);
    }

    @Override
    public synchronized void seekTo(long positionMs) {
        if (!prepared || currentSource == null || !currentSource.isSeekable()) {
            return;
        }
        try {
            int target = (int) Math.max(0L, Math.min(positionMs, safeGetDurationLocked()));
            mediaPlayer.seekTo(target);
            playerJni.seekTo(target);
            updateSnapshotLocked(snapshot.getState(), target, safeGetDurationLocked(), null);
        } catch (IllegalStateException ignored) {
            updateSnapshotLocked(PlayerKernelState.ERROR, 0L, 0L, "Seek failed");
        }
    }

    @Override
    public synchronized void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        try {
            mediaPlayer.setVolume(this.volume, this.volume);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public synchronized long getCurrentPosition() {
        return safeGetCurrentPositionLocked();
    }

    @Override
    public synchronized long getDuration() {
        return safeGetDurationLocked();
    }

    @Override
    public synchronized boolean isPlaying() {
        try {
            return prepared && mediaPlayer.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public synchronized boolean isNativeReady() {
        return playerJni.isReady();
    }

    @NonNull
    @Override
    public synchronized PlayerKernelSnapshot getSnapshot() {
        return new PlayerKernelSnapshot(
                snapshot.getState(),
                safeGetCurrentPositionLocked(),
                Math.max(snapshot.getDurationMs(), safeGetDurationLocked()),
                currentSource != null && currentSource.isSeekable(),
                playerJni.isReady(),
                currentSource,
                snapshot.getErrorMessage());
    }

    @Override
    public synchronized void release() {
        listeners.clear();
        prepared = false;
        currentSource = null;
        playerJni.release();
        mediaPlayer.release();
        snapshot = PlayerKernelSnapshot.idle();
    }

    private void startPlaybackLocked() {
        if (!prepared) {
            return;
        }
        try {
            mediaPlayer.setVolume(volume, volume);
            mediaPlayer.start();
            playerJni.play();
            lastSteadyState = PlayerKernelState.PLAYING;
            updateSnapshotLocked(PlayerKernelState.PLAYING, safeGetCurrentPositionLocked(), safeGetDurationLocked(), null);
        } catch (IllegalStateException ignored) {
            updateSnapshotLocked(PlayerKernelState.ERROR, 0L, 0L, "Play failed");
        }
    }

    private void setMediaPlayerDataSource(@NonNull ResolvedPlayableSource source) throws IOException {
        Uri uri = Uri.parse(source.getResolvedUrl());
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "android.resource".equalsIgnoreCase(scheme)) {
            mediaPlayer.setDataSource(appContext, uri);
            return;
        }
        if ("file".equalsIgnoreCase(scheme) || (TextUtils.isEmpty(scheme) && source.isLocalSource())) {
            mediaPlayer.setDataSource(source.getResolvedUrl());
            return;
        }
        Map<String, String> headers = TextUtils.isEmpty(source.getUserAgent())
                ? Collections.emptyMap()
                : Collections.singletonMap("User-Agent", source.getUserAgent());
        mediaPlayer.setDataSource(appContext, uri, headers);
    }

    private long safeGetCurrentPositionLocked() {
        try {
            return prepared ? mediaPlayer.getCurrentPosition() : snapshot.getCurrentPositionMs();
        } catch (IllegalStateException ignored) {
            return snapshot.getCurrentPositionMs();
        }
    }

    private long safeGetDurationLocked() {
        try {
            if (!prepared) {
                return snapshot.getDurationMs();
            }
            return Math.max(mediaPlayer.getDuration(), 0);
        } catch (IllegalStateException ignored) {
            return snapshot.getDurationMs();
        }
    }

    private void updateSnapshotLocked(@NonNull PlayerKernelState state,
                                      long currentPositionMs,
                                      long durationMs,
                                      String errorMessage) {
        snapshot = new PlayerKernelSnapshot(
                state,
                currentPositionMs,
                durationMs,
                currentSource != null && currentSource.isSeekable(),
                playerJni.isReady(),
                currentSource,
                errorMessage);
        dispatchSnapshot(snapshot);
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
}
