package com.example.flexmusicplayer.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PlaybackController {

    public interface Listener {
        void onPlaybackStateChanged(@NonNull PlayerState state);
    }

    private static final String[] DEMO_STREAMS = {
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
    };
    private static PlaybackController instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private final PlayerState playerState = new PlayerState();
    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            synchronized (PlaybackController.this) {
                if (playerState.getCurrentSong() == null) {
                    return;
                }
                try {
                    if (mediaPlayer.isPlaying() || playerState.isPaused()) {
                        playerState.setCurrentPosition(mediaPlayer.getCurrentPosition());
                        playerState.setDuration(Math.max(mediaPlayer.getDuration(), playerState.getDuration()));
                        dispatchState();
                    }
                    if (mediaPlayer.isPlaying()) {
                        mainHandler.postDelayed(this, 500);
                    }
                } catch (IllegalStateException ignored) {
                }
            }
        }
    };

    private final List<Song> queue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean prepared;

    private PlaybackController(@NonNull Context context) {
        appContext = context.getApplicationContext();
        configureMediaPlayer();
    }

    public static synchronized PlaybackController getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new PlaybackController(context);
        }
        return instance;
    }

    private void configureMediaPlayer() {
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setOnPreparedListener(mp -> {
            synchronized (PlaybackController.this) {
                prepared = true;
                playerState.setDuration(Math.max(mp.getDuration(), playerState.getDuration()));
                playerState.setState(PlayerState.State.PLAYING);
                mp.start();
                startProgressTicker();
                dispatchState();
            }
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            synchronized (PlaybackController.this) {
                if (playerState.getRepeatMode() == PlayerState.RepeatMode.ONE) {
                    mp.seekTo(0);
                    mp.start();
                    startProgressTicker();
                    dispatchState();
                    return;
                }
                if (hasNextInternal()) {
                    playQueue(queue, resolveNextIndex());
                    return;
                }
                playerState.setCurrentPosition(playerState.getDuration());
                playerState.setState(PlayerState.State.PAUSED);
                stopProgressTicker();
                dispatchState();
            }
        });
        mediaPlayer.setOnInfoListener((mp, what, extra) -> {
            synchronized (PlaybackController.this) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    playerState.setState(PlayerState.State.LOADING);
                    dispatchState();
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    playerState.setState(mp.isPlaying() ? PlayerState.State.PLAYING : PlayerState.State.PAUSED);
                    dispatchState();
                }
            }
            return false;
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            synchronized (PlaybackController.this) {
                prepared = false;
                stopProgressTicker();
                playerState.setState(PlayerState.State.ERROR);
                dispatchState();
            }
            return true;
        });
    }

    public synchronized void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        notifyListener(listener, snapshotState());
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public synchronized PlayerState getPlayerState() {
        return snapshotState();
    }

    public synchronized void playSong(@NonNull Song song) {
        List<Song> single = new ArrayList<>();
        single.add(song);
        playQueue(single, 0);
    }

    public synchronized void playQueue(@NonNull List<Song> songs, int index) {
        if (songs.isEmpty()) {
            return;
        }
        queue.clear();
        queue.addAll(songs);
        currentIndex = Math.max(0, Math.min(index, queue.size() - 1));
        prepareCurrentSong();
    }

    public synchronized void togglePlayPause() {
        if (playerState.getCurrentSong() == null) {
            return;
        }
        try {
            if (prepared && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                playerState.setState(PlayerState.State.PAUSED);
                stopProgressTicker();
            } else if (prepared) {
                mediaPlayer.start();
                playerState.setState(PlayerState.State.PLAYING);
                startProgressTicker();
            } else {
                prepareCurrentSong();
                return;
            }
            dispatchState();
        } catch (IllegalStateException e) {
            playerState.setState(PlayerState.State.ERROR);
            dispatchState();
        }
    }

    public synchronized void seekTo(int positionMs) {
        if (!prepared) {
            return;
        }
        int safePosition = Math.max(0, Math.min(positionMs, playerState.getDuration()));
        mediaPlayer.seekTo(safePosition);
        playerState.setCurrentPosition(safePosition);
        dispatchState();
    }

    public synchronized void skipNext() {
        if (!hasNextInternal()) {
            return;
        }
        currentIndex = resolveNextIndex();
        prepareCurrentSong();
    }

    public synchronized void skipPrevious() {
        if (prepared && playerState.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        if (!hasPreviousInternal()) {
            seekTo(0);
            return;
        }
        if (playerState.isShuffleEnabled() && queue.size() > 1) {
            currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
        } else if (currentIndex > 0) {
            currentIndex--;
        } else if (playerState.getRepeatMode() == PlayerState.RepeatMode.ALL) {
            currentIndex = queue.size() - 1;
        }
        prepareCurrentSong();
    }

    public synchronized void toggleShuffle() {
        playerState.toggleShuffle();
        dispatchState();
    }

    public synchronized void toggleRepeat() {
        playerState.toggleRepeat();
        dispatchState();
    }

    public synchronized boolean hasNext() {
        return hasNextInternal();
    }

    public synchronized boolean hasPrevious() {
        return hasPreviousInternal();
    }

    private void prepareCurrentSong() {
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        Song song = queue.get(currentIndex);
        playerState.setCurrentSong(song);
        playerState.setCurrentPosition(0);
        playerState.setDuration(song.getDuration());
        playerState.setState(PlayerState.State.LOADING);
        prepared = false;
        stopProgressTicker();

        try {
            mediaPlayer.reset();
            setDataSource(song);
            mediaPlayer.prepareAsync();
            dispatchState();
        } catch (IOException | RuntimeException e) {
            playerState.setState(PlayerState.State.ERROR);
            dispatchState();
        }
    }

    private void setDataSource(@NonNull Song song) throws IOException {
        String source = resolvePlayableSource(song);
        Uri uri = Uri.parse(source);
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme) || "android.resource".equalsIgnoreCase(scheme)) {
            mediaPlayer.setDataSource(appContext, uri);
            return;
        }
        if (scheme == null && source.startsWith("/")) {
            mediaPlayer.setDataSource(source);
            return;
        }
        mediaPlayer.setDataSource(source);
    }

    private String resolvePlayableSource(@NonNull Song song) {
        String source = song.getAudioUrl();
        if (TextUtils.isEmpty(source)) {
            return resolveDemoStream(song);
        }
        Uri uri = Uri.parse(source);
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "android.resource".equalsIgnoreCase(scheme)) {
            return source;
        }
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null && new File(path).exists()) {
                return source;
            }
            return resolveDemoStream(song);
        }
        if (source.startsWith("/") && new File(source).exists()) {
            return source;
        }
        return resolveDemoStream(song);
    }

    private String resolveDemoStream(@NonNull Song song) {
        int index = (int) (Math.abs(song.getId()) % DEMO_STREAMS.length);
        return DEMO_STREAMS[index];
    }

    private int resolveNextIndex() {
        if (queue.isEmpty()) {
            return -1;
        }
        if (playerState.isShuffleEnabled() && queue.size() > 1) {
            return (currentIndex + 1 + ((currentIndex + 1) % (queue.size() - 1))) % queue.size();
        }
        if (currentIndex < queue.size() - 1) {
            return currentIndex + 1;
        }
        return playerState.getRepeatMode() == PlayerState.RepeatMode.ALL ? 0 : currentIndex;
    }

    private boolean hasNextInternal() {
        return queue.size() > 1 && (playerState.isShuffleEnabled()
                || currentIndex < queue.size() - 1
                || playerState.getRepeatMode() == PlayerState.RepeatMode.ALL);
    }

    private boolean hasPreviousInternal() {
        return queue.size() > 1 && (currentIndex > 0 || playerState.getRepeatMode() == PlayerState.RepeatMode.ALL);
    }

    private void startProgressTicker() {
        mainHandler.removeCallbacks(progressTicker);
        mainHandler.post(progressTicker);
    }

    private void stopProgressTicker() {
        mainHandler.removeCallbacks(progressTicker);
    }

    private PlayerState snapshotState() {
        PlayerState snapshot = new PlayerState();
        snapshot.setState(playerState.getState());
        snapshot.setCurrentSong(playerState.getCurrentSong());
        snapshot.setCurrentPosition(playerState.getCurrentPosition());
        snapshot.setDuration(playerState.getDuration());
        snapshot.setShuffleEnabled(playerState.isShuffleEnabled());
        snapshot.setRepeatMode(playerState.getRepeatMode());
        snapshot.setVolume(playerState.getVolume());
        snapshot.setPlaybackSpeed(playerState.getPlaybackSpeed());
        return snapshot;
    }

    private void dispatchState() {
        PlayerState snapshot = snapshotState();
        for (Listener listener : listeners) {
            notifyListener(listener, snapshot);
        }
    }

    private void notifyListener(@NonNull Listener listener, @NonNull PlayerState snapshot) {
        mainHandler.post(() -> listener.onPlaybackStateChanged(snapshot));
    }
}
