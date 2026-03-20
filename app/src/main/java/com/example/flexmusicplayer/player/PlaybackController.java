package com.example.flexmusicplayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_data.player.PlaybackWarmupCoordinator;
import com.example.core_domain.player.IPlaybackWarmupEngine;
import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.player.PlaybackSourceResolver;
import com.example.core_domain.player.PlaybackWarmupRequest;
import com.example.core_domain.player.PlaybackWarmupSnapshot;
import com.example.core_domain.player.PlayerKernel;
import com.example.core_domain.player.PlayerKernelSnapshot;
import com.example.core_domain.player.PlayerKernelState;
import com.example.core_domain.player.ResolvedPlayableSource;
import com.example.core_domain.search.SearchTrack;
import com.example.core_network.stream.NetworkPlaybackSourceResolver;
import com.example.feature_player.player.FeaturePlayerFactory;
import com.example.flexmusicplayer.model.PlayerState;
import com.example.flexmusicplayer.model.Song;
import com.example.flexmusicplayer.storage.RecentPlaybackStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaybackController {

    private static final String TAG = "PlaybackController";

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
    private final RecentPlaybackStore recentPlaybackStore = RecentPlaybackStore.getInstance();
    private final ExecutorService sourceResolveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService warmupExecutor = Executors.newSingleThreadExecutor();
    private final PlayerKernel playerKernel;
    private final PlaybackSourceResolver playbackSourceResolver;
    private final IPlaybackWarmupEngine playbackWarmupEngine;
    private final PlaybackWarmupCoordinator playbackWarmupCoordinator = new PlaybackWarmupCoordinator();
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            synchronized (PlaybackController.this) {
                if (playerState.getCurrentSong() == null) {
                    return;
                }
                PlayerKernelSnapshot snapshot = playerKernel.getSnapshot();
                playerState.setCurrentPosition((int) snapshot.getCurrentPositionMs());
                playerState.setDuration((int) Math.max(snapshot.getDurationMs(), playerState.getDuration()));
                dispatchState();
                if (snapshot.getState() == PlayerKernelState.PLAYING) {
                    mainHandler.postDelayed(this, 500);
                }
            }
        }
    };

    private final List<Song> queue = new ArrayList<>();
    private int currentIndex = -1;
    private long prepareGeneration = 0L;
    private long warmupGeneration = 0L;
    private String pendingRecentKey = "";
    private String activeWarmupSourceId = "";
    private String lastWarmupPlanKey = "";

    private PlaybackController(@NonNull Context context) {
        appContext = context.getApplicationContext();
        playerKernel = FeaturePlayerFactory.create(appContext);
        NetworkPlaybackSourceResolver networkPlaybackSourceResolver = new NetworkPlaybackSourceResolver();
        playbackSourceResolver = networkPlaybackSourceResolver;
        playbackWarmupEngine = networkPlaybackSourceResolver;
        playerKernel.addListener(this::handleKernelSnapshotChanged);
    }

    public static synchronized PlaybackController getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new PlaybackController(context);
        }
        return instance;
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

    public synchronized void playSearchTrack(@NonNull SearchTrack track,
                                             @NonNull PlaybackRequest request) {
        Log.d(TAG, "playSearchTrack trackId=" + track.getTrackId()
                + " providerId=" + track.getProviderId()
                + " sourceId=" + request.getSourceId()
                + " url=" + request.getOriginalUrl());
        playSong(buildSearchSong(track, request));
    }

    public synchronized void playSearchQueue(@NonNull List<SearchTrack> tracks,
                                             @NonNull List<PlaybackRequest> requests,
                                             int startIndex) {
        List<Song> songs = buildSearchSongs(tracks, requests);
        if (songs.isEmpty()) {
            return;
        }
        Log.d(TAG, "playSearchQueue size=" + songs.size() + " startIndex=" + startIndex);
        playQueue(songs, startIndex);
    }

    public synchronized void addSearchTrackNext(@NonNull SearchTrack track,
                                                @NonNull PlaybackRequest request) {
        Song song = buildSearchSong(track, request);
        if (queue.isEmpty()) {
            playSong(song);
            return;
        }
        int insertIndex = Math.max(0, Math.min(currentIndex + 1, queue.size()));
        queue.add(insertIndex, song);
        Log.d(TAG, "addSearchTrackNext sourceId=" + request.getSourceId()
                + " insertIndex=" + insertIndex
                + " queueSize=" + queue.size());
        onQueueTopologyChangedLocked();
        dispatchState();
    }

    public synchronized void addSearchTrackToQueue(@NonNull SearchTrack track,
                                                   @NonNull PlaybackRequest request) {
        Song song = buildSearchSong(track, request);
        if (queue.isEmpty()) {
            playSong(song);
            return;
        }
        queue.add(song);
        Log.d(TAG, "addSearchTrackToQueue sourceId=" + request.getSourceId()
                + " queueSize=" + queue.size());
        onQueueTopologyChangedLocked();
        dispatchState();
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

    public synchronized void playQueueIndex(int index) {
        if (queue.isEmpty()) {
            return;
        }
        currentIndex = Math.max(0, Math.min(index, queue.size() - 1));
        prepareCurrentSong();
    }

    public synchronized void togglePlayPause() {
        if (playerState.getCurrentSong() == null) {
            return;
        }
        PlayerKernelState kernelState = playerKernel.getSnapshot().getState();
        if (kernelState == PlayerKernelState.PLAYING
                || kernelState == PlayerKernelState.BUFFERING
                || kernelState == PlayerKernelState.PREPARING) {
            pause();
            return;
        }
        if (kernelState == PlayerKernelState.PAUSED
                || kernelState == PlayerKernelState.READY
                || kernelState == PlayerKernelState.COMPLETED) {
            playerKernel.play();
            return;
        }
        prepareCurrentSong();
    }

    public synchronized void pause() {
        PlayerKernelState kernelState = playerKernel.getSnapshot().getState();
        if (kernelState != PlayerKernelState.PLAYING
                && kernelState != PlayerKernelState.BUFFERING
                && kernelState != PlayerKernelState.PREPARING
                && kernelState != PlayerKernelState.READY) {
            return;
        }
        playerKernel.pause();
    }

    public synchronized void seekTo(int positionMs) {
        if (playerState.getCurrentSong() == null || playerState.getCurrentSong().isRadioStream()) {
            return;
        }
        if (!playerKernel.getSnapshot().isSeekable()) {
            return;
        }
        int safePosition = Math.max(0, Math.min(positionMs, playerState.getDuration()));
        playerKernel.seekTo(safePosition);
        playerState.setCurrentPosition(safePosition);
        dispatchState();
    }

    public synchronized void setVolume(float volume) {
        float safeVolume = Math.max(0f, Math.min(1f, volume));
        playerState.setVolume(safeVolume);
        playerKernel.setVolume(safeVolume);
        dispatchState();
    }

    public synchronized void setPlaybackSpeed(float playbackSpeed) {
        float safeSpeed = Math.max(0.5f, Math.min(2.0f, playbackSpeed));
        playerState.setPlaybackSpeed(safeSpeed);
        Log.d(TAG, "setPlaybackSpeed speed=" + safeSpeed + " nativeSupported=false");
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
        if (playerState.getCurrentPosition() > 3000) {
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
        onQueueTopologyChangedLocked();
        dispatchState();
    }

    public synchronized void toggleRepeat() {
        playerState.toggleRepeat();
        onQueueTopologyChangedLocked();
        dispatchState();
    }

    public synchronized void setRepeatMode(@NonNull PlayerState.RepeatMode repeatMode) {
        if (playerState.getRepeatMode() == repeatMode) {
            return;
        }
        playerState.setRepeatMode(repeatMode);
        onQueueTopologyChangedLocked();
        dispatchState();
    }

    public synchronized boolean hasNext() {
        return hasNextInternal();
    }

    public synchronized boolean hasPrevious() {
        return hasPreviousInternal();
    }

    @NonNull
    public synchronized List<Song> getQueueSnapshot() {
        return new ArrayList<>(queue);
    }

    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    private void prepareCurrentSong() {
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        Song song = queue.get(currentIndex);
        resetWarmupStateForCurrentSongLocked(resolveSourceId(song));
        Log.d(TAG, "prepareCurrentSong id=" + song.getId()
                + " title=" + song.getTitle()
                + " url=" + song.getAudioUrl()
                + " local=" + song.isLocal()
                + " radio=" + song.isRadioStream());
        playerState.setCurrentSong(song);
        playerState.setCurrentPosition(0);
        playerState.setDuration(song.getDuration());
        playerState.setState(PlayerState.State.LOADING);
        stopProgressTicker();
        pendingRecentKey = buildPlaybackKey(song);
        long generation = ++prepareGeneration;
        dispatchState();

        sourceResolveExecutor.execute(() -> resolveAndPrepare(song, generation));
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

    private void resolveAndPrepare(@NonNull Song song, long generation) {
        long startedAtMs = System.currentTimeMillis();
        try {
            String source = resolvePlayableSource(song);
            Log.d(TAG, "resolveAndPrepare sourceId=" + resolveSourceId(song) + " source=" + source);
            PlaybackRequest request = new PlaybackRequest(resolveSourceId(song), source, song.isRadioStream());
            ResolvedPlayableSource resolvedSource = playbackSourceResolver.resolve(request);
            Log.d(TAG, "resolveAndPrepare resolved sourceId=" + resolvedSource.getSourceId()
                    + " elapsedMs=" + Math.max(System.currentTimeMillis() - startedAtMs, 0L)
                    + " resolvedUrl=" + resolvedSource.getResolvedUrl());
            onSourceResolved(generation, resolvedSource);
        } catch (IOException e) {
            Log.e(TAG, "resolveAndPrepare failed sourceId=" + resolveSourceId(song), e);
            onSourceResolveFailed(generation);
        }
    }

    private synchronized void onSourceResolved(long generation, @NonNull ResolvedPlayableSource resolvedSource) {
        if (generation != prepareGeneration) {
            return;
        }
        Log.d(TAG, "onSourceResolved sourceId=" + resolvedSource.getSourceId()
                + " originalUrl=" + resolvedSource.getOriginalUrl()
                + " resolvedUrl=" + resolvedSource.getResolvedUrl()
                + " local=" + resolvedSource.isLocalSource()
                + " seekable=" + resolvedSource.isSeekable());
        try {
            playerKernel.prepare(resolvedSource);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "playerKernel.prepare failed sourceId=" + resolvedSource.getSourceId(), e);
            playerState.setState(PlayerState.State.ERROR);
            dispatchState();
        }
    }

    private synchronized void onSourceResolveFailed(long generation) {
        if (generation != prepareGeneration) {
            return;
        }
        playerState.setState(PlayerState.State.ERROR);
        stopProgressTicker();
        dispatchState();
    }

    private synchronized void handleKernelSnapshotChanged(@NonNull PlayerKernelSnapshot snapshot) {
        Song currentSong = playerState.getCurrentSong();
        if (currentSong == null) {
            return;
        }
        Log.d(TAG, "kernelSnapshot state=" + snapshot.getState()
                + " position=" + snapshot.getCurrentPositionMs()
                + " duration=" + snapshot.getDurationMs()
                + " nativeReady=" + snapshot.isNativeReady()
                + " error=" + snapshot.getErrorMessage());

        playerState.setCurrentPosition((int) snapshot.getCurrentPositionMs());
        playerState.setDuration((int) Math.max(snapshot.getDurationMs(), playerState.getDuration()));

        switch (snapshot.getState()) {
            case PREPARING:
                playerState.setState(PlayerState.State.LOADING);
                stopProgressTicker();
                break;
            case READY:
                playerState.setState(PlayerState.State.PAUSED);
                stopProgressTicker();
                break;
            case BUFFERING:
                playerState.setState(PlayerState.State.LOADING);
                if (currentSong.isRadioStream()) {
                    stopProgressTicker();
                } else {
                    startProgressTicker();
                }
                break;
            case PLAYING:
                playerState.setState(PlayerState.State.PLAYING);
                if (currentSong.isRadioStream()) {
                    stopProgressTicker();
                } else {
                    startProgressTicker();
                }
                if (pendingRecentKey.equals(buildPlaybackKey(currentSong))) {
                    recentPlaybackStore.recordPlayback(currentSong);
                    pendingRecentKey = "";
                }
                scheduleWarmupLocked();
                break;
            case PAUSED:
                playerState.setState(PlayerState.State.PAUSED);
                stopProgressTicker();
                break;
            case COMPLETED:
                handleCompletionLocked();
                return;
            case ERROR:
                playerState.setState(PlayerState.State.ERROR);
                stopProgressTicker();
                break;
            case IDLE:
            default:
                playerState.setState(PlayerState.State.IDLE);
                stopProgressTicker();
                break;
        }
        dispatchState();
    }

    private void handleCompletionLocked() {
        if (playerState.getRepeatMode() == PlayerState.RepeatMode.ONE) {
            playerKernel.seekTo(0L);
            playerKernel.play();
            return;
        }
        if (hasNextInternal()) {
            currentIndex = resolveNextIndex();
            prepareCurrentSong();
            return;
        }
        playerState.setCurrentPosition(playerState.getDuration());
        playerState.setState(PlayerState.State.PAUSED);
        stopProgressTicker();
        dispatchState();
    }

    @NonNull
    private String resolveSourceId(@NonNull Song song) {
        if (!TextUtils.isEmpty(song.getSourceId())) {
            return song.getSourceId();
        }
        return String.valueOf(song.getId());
    }

    @NonNull
    private String buildPlaybackKey(@NonNull Song song) {
        String sourceId = resolveSourceId(song);
        return sourceId + "|" + song.getAudioUrl();
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
        mainHandler.post(() -> {
            synchronized (PlaybackController.this) {
                if (!listeners.contains(listener)) {
                    return;
                }
            }
            listener.onPlaybackStateChanged(snapshot);
        });
    }

    private void onQueueTopologyChangedLocked() {
        lastWarmupPlanKey = "";
        if (playerState.getState() == PlayerState.State.PLAYING) {
            scheduleWarmupLocked();
        }
    }

    private void resetWarmupStateForCurrentSongLocked(@NonNull String currentSourceId) {
        if (!TextUtils.isEmpty(activeWarmupSourceId) && activeWarmupSourceId.equals(currentSourceId)) {
            return;
        }
        lastWarmupPlanKey = "";
        if (!TextUtils.isEmpty(activeWarmupSourceId) && !activeWarmupSourceId.equals(currentSourceId)) {
            playbackWarmupEngine.cancelWarmup(activeWarmupSourceId);
            activeWarmupSourceId = "";
        }
    }

    private void scheduleWarmupLocked() {
        List<PlaybackRequest> queueRequests = buildQueueRequestsLocked();
        PlaybackWarmupRequest warmupRequest = playbackWarmupCoordinator.planWarmup(
                queueRequests,
                currentIndex,
                resolveNextIndex());
        if (warmupRequest == null) {
            if (!TextUtils.isEmpty(activeWarmupSourceId)) {
                playbackWarmupEngine.cancelWarmup(activeWarmupSourceId);
                activeWarmupSourceId = "";
            }
            lastWarmupPlanKey = "";
            return;
        }

        String warmupPlanKey = warmupRequest.getSourceId()
                + "|" + warmupRequest.getOriginalUrl()
                + "|" + warmupRequest.getTargetLevel();
        if (warmupPlanKey.equals(lastWarmupPlanKey)) {
            return;
        }

        if (!TextUtils.isEmpty(activeWarmupSourceId) && !activeWarmupSourceId.equals(warmupRequest.getSourceId())) {
            playbackWarmupEngine.cancelWarmup(activeWarmupSourceId);
        }

        activeWarmupSourceId = warmupRequest.getSourceId();
        lastWarmupPlanKey = warmupPlanKey;
        long generation = ++warmupGeneration;
        warmupExecutor.execute(() -> executeWarmup(warmupRequest, warmupPlanKey, generation));
    }

    private void executeWarmup(@NonNull PlaybackWarmupRequest warmupRequest,
                               @NonNull String warmupPlanKey,
                               long generation) {
        PlaybackWarmupSnapshot snapshot = playbackWarmupEngine.warmup(warmupRequest);
        synchronized (this) {
            boolean stalePlan = generation != warmupGeneration
                    || !warmupRequest.getSourceId().equals(activeWarmupSourceId)
                    || !warmupPlanKey.equals(lastWarmupPlanKey);
            if (stalePlan) {
                playbackWarmupEngine.cancelWarmup(warmupRequest.getSourceId());
                Log.d(TAG, "warmup stale sourceId=" + warmupRequest.getSourceId()
                        + " completed=" + snapshot.getCompletedLevel());
                return;
            }
            Log.d(TAG, "warmup tracked sourceId=" + warmupRequest.getSourceId()
                    + " completed=" + snapshot.getCompletedLevel()
                    + " totalLatencyMs=" + snapshot.getTotalLatencyMs());
        }
    }

    @NonNull
    private List<PlaybackRequest> buildQueueRequestsLocked() {
        List<PlaybackRequest> requests = new ArrayList<>(queue.size());
        for (Song queuedSong : queue) {
            String source = resolvePlayableSource(queuedSong);
            requests.add(new PlaybackRequest(
                    resolveSourceId(queuedSong),
                    source,
                    queuedSong.isRadioStream()));
        }
        return requests;
    }

    @NonNull
    private List<Song> buildSearchSongs(@NonNull List<SearchTrack> tracks,
                                        @NonNull List<PlaybackRequest> requests) {
        List<Song> songs = new ArrayList<>();
        int count = Math.min(tracks.size(), requests.size());
        for (int index = 0; index < count; index++) {
            songs.add(buildSearchSong(tracks.get(index), requests.get(index)));
        }
        return songs;
    }

    @NonNull
    private Song buildSearchSong(@NonNull SearchTrack track, @NonNull PlaybackRequest request) {
        Song song = new Song(
                Math.abs((long) request.getSourceId().hashCode()),
                track.getTitle(),
                TextUtils.join(" / ", track.getArtistNames()),
                track.getAlbumName(),
                (int) track.getDurationMs(),
                request.getOriginalUrl());
        song.setAlbumArtUrl(track.getCoverUrl());
        song.setLocal(false);
        song.setRadioStream(request.isLiveStream());
        song.setSourceId(request.getSourceId());
        return song;
    }
}
