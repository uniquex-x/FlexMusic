package com.example.flexmusicplayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_data.player.PlaybackWarmupCoordinator;
import com.example.core_data.search.OnlineSearchRepository;
import com.example.core_data.search.TrackPlaybackRepository;
import com.example.core_domain.player.IPlaybackWarmupEngine;
import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.player.PlaybackSourceResolver;
import com.example.core_domain.player.PlaybackWarmupRequest;
import com.example.core_domain.player.PlaybackWarmupSnapshot;
import com.example.core_domain.player.PlayerKernel;
import com.example.core_domain.player.PlayerKernelSnapshot;
import com.example.core_domain.player.PlayerKernelState;
import com.example.core_domain.player.ResolvedPlayableSource;
import com.example.core_domain.search.PlayTrackFromSearchUseCase;
import com.example.core_domain.search.SearchFilter;
import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchScope;
import com.example.core_domain.search.SearchTrack;
import com.example.core_domain.search.SearchUseCase;
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
    private static final int SEARCH_QUEUE_INITIAL_SIZE = 20;
    private static final int SEARCH_QUEUE_EXPAND_THRESHOLD = 5;
    private static final int SEARCH_QUEUE_MAX_SIZE = 1000;
    private static final String SEARCH_TRACK_SOURCE_PREFIX = "search_track:";

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
    private final ExecutorService searchQueueExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService searchWarmupResolveExecutor = Executors.newSingleThreadExecutor();
    private final PlayerKernel playerKernel;
    private final PlaybackSourceResolver playbackSourceResolver;
    private final IPlaybackWarmupEngine playbackWarmupEngine;
    private final SearchUseCase searchUseCase = new SearchUseCase(new OnlineSearchRepository());
    private final PlayTrackFromSearchUseCase playTrackFromSearchUseCase =
            new PlayTrackFromSearchUseCase(new TrackPlaybackRepository());
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
    @Nullable
    private SearchQueueSession searchQueueSession;
    private long searchQueueSessionGeneration = 0L;
    private boolean searchQueueExpansionInFlight = false;
    private boolean searchWarmupResolveInFlight = false;
    private String activeSearchWarmupResolveKey = "";

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
        clearSearchQueueSessionLocked();
        playSong(buildSearchSong(track, request));
    }

    public synchronized void playSearchResultPage(@NonNull SearchResultPage resultPage,
                                                  int startIndex,
                                                  @NonNull PlaybackRequest initialRequest) {
        List<SearchTrack> tracks = resultPage.getTracks();
        if (tracks.isEmpty()) {
            return;
        }
        int safeStartIndex = Math.max(0, Math.min(startIndex, tracks.size() - 1));
        SearchQueueSession session = new SearchQueueSession(resultPage);
        int initialQueueSize = Math.min(
                session.getMaxQueueSize(),
                Math.max(SEARCH_QUEUE_INITIAL_SIZE, safeStartIndex + 1));
        List<Song> songs = buildSearchSongs(tracks.subList(0, Math.min(initialQueueSize, tracks.size())));
        if (songs.isEmpty()) {
            return;
        }
        Song startSong = songs.get(safeStartIndex);
        cacheResolvedSearchRequestLocked(session, startSong, initialRequest);
        queue.clear();
        queue.addAll(songs);
        currentIndex = safeStartIndex;
        searchQueueSession = session;
        searchQueueSession.setEnqueuedTrackCount(songs.size());
        searchQueueSessionGeneration++;
        searchQueueExpansionInFlight = false;
        Log.d(TAG, "playSearchResultPage keyword=" + resultPage.getKeyword()
                + " initialQueueSize=" + songs.size()
                + " startIndex=" + safeStartIndex
                + " totalCount=" + resultPage.getTotalCount());
        prepareCurrentSong();
        ensureSearchQueueCapacityLocked(initialQueueSize);
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
        clearSearchQueueSessionLocked();
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
        } else {
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

        scheduleSearchQueueExpansionIfNeededLocked();
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
            PlaybackRequest request = resolvePlaybackRequest(song);
            Log.d(TAG, "resolveAndPrepare sourceId=" + resolveSourceId(song) + " source=" + request.getOriginalUrl());
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

    @NonNull
    private PlaybackRequest resolvePlaybackRequest(@NonNull Song song) throws IOException {
        String searchKey = resolveSourceId(song);
        if (isSearchQueueSong(song)) {
            PlaybackRequest cachedRequest;
            SearchTrack searchTrack;
            synchronized (this) {
                cachedRequest = resolveCachedSearchRequestLocked(searchKey);
                searchTrack = findSearchTrackLocked(searchKey);
            }
            if (cachedRequest != null) {
                synchronized (this) {
                    updateSearchSongFromRequestLocked(song, cachedRequest);
                }
                return new PlaybackRequest(searchKey, cachedRequest.getOriginalUrl(), cachedRequest.isLiveStream());
            }
            if (searchTrack != null) {
                PlaybackRequest resolvedRequest = playTrackFromSearchUseCase.execute(searchTrack);
                synchronized (this) {
                    PlaybackRequest sessionRequest = resolveCachedSearchRequestLocked(searchKey);
                    if (sessionRequest != null) {
                        updateSearchSongFromRequestLocked(song, sessionRequest);
                        return new PlaybackRequest(searchKey, sessionRequest.getOriginalUrl(), sessionRequest.isLiveStream());
                    }
                    if (searchQueueSession != null) {
                        searchQueueSession.putResolvedRequest(searchKey, resolvedRequest);
                    }
                    updateSearchSongFromRequestLocked(song, resolvedRequest);
                }
                Log.d(TAG, "resolved search queue track sourceId=" + searchKey
                        + " url=" + resolvedRequest.getOriginalUrl());
                return new PlaybackRequest(searchKey, resolvedRequest.getOriginalUrl(), resolvedRequest.isLiveStream());
            }
        }
        String source = resolvePlayableSource(song);
        return new PlaybackRequest(resolveSourceId(song), source, song.isRadioStream());
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
                scheduleSearchQueueExpansionIfNeededLocked();
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
        return queue.size() > 1 ? 0 : currentIndex;
    }

    private boolean hasNextInternal() {
        return queue.size() > 1;
    }

    private boolean hasPreviousInternal() {
        return queue.size() > 1;
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
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        Song currentSong = queue.get(currentIndex);
        PlaybackRequest currentRequest = buildWarmupPlaybackRequestLocked(currentSong);
        if (currentRequest == null) {
            return;
        }

        int nextIndex = resolveNextIndex();
        PlaybackRequest nextRequest = null;
        if (nextIndex >= 0 && nextIndex < queue.size() && nextIndex != currentIndex) {
            Song nextSong = queue.get(nextIndex);
            nextRequest = buildWarmupPlaybackRequestLocked(nextSong);
            if (nextRequest == null && isSearchQueueSong(nextSong)) {
                scheduleSearchWarmupResolutionLocked(nextSong);
            }
        }

        List<PlaybackRequest> warmupCandidates = new ArrayList<>(2);
        warmupCandidates.add(currentRequest);
        int nextWarmupIndex = -1;
        if (nextRequest != null) {
            warmupCandidates.add(nextRequest);
            nextWarmupIndex = 1;
        }
        PlaybackWarmupRequest warmupRequest = playbackWarmupCoordinator.planWarmup(
                warmupCandidates,
                0,
                nextWarmupIndex);
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
    private List<Song> buildSearchSongs(@NonNull List<SearchTrack> tracks) {
        List<Song> songs = new ArrayList<>(tracks.size());
        for (SearchTrack track : tracks) {
            songs.add(buildSearchSong(track));
        }
        return songs;
    }

    @NonNull
    private Song buildSearchSong(@NonNull SearchTrack track, @NonNull PlaybackRequest request) {
        Song song = buildSearchSong(track);
        updateSearchSongFromRequestLocked(song, request);
        return song;
    }

    @NonNull
    private Song buildSearchSong(@NonNull SearchTrack track) {
        String searchTrackKey = buildSearchTrackKey(track);
        Song song = new Song(
                Math.abs((long) searchTrackKey.hashCode()),
                track.getTitle(),
                TextUtils.join(" / ", track.getArtistNames()),
                track.getAlbumName(),
                (int) track.getDurationMs(),
                "");
        song.setAlbumArtUrl(track.getCoverUrl());
        song.setLocal(false);
        song.setRadioStream(false);
        song.setSourceId(searchTrackKey);
        return song;
    }

    private void updateSearchSongFromRequestLocked(@NonNull Song song, @NonNull PlaybackRequest request) {
        song.setAudioUrl(request.getOriginalUrl());
        song.setRadioStream(request.isLiveStream());
    }

    @Nullable
    private PlaybackRequest resolveCachedSearchRequestLocked(@NonNull String searchKey) {
        if (searchQueueSession == null) {
            return null;
        }
        return searchQueueSession.getResolvedRequest(searchKey);
    }

    @Nullable
    private SearchTrack findSearchTrackLocked(@NonNull String searchKey) {
        if (searchQueueSession == null) {
            return null;
        }
        return searchQueueSession.findTrack(searchKey);
    }

    private void cacheResolvedSearchRequestLocked(@NonNull SearchQueueSession session,
                                                  @NonNull Song song,
                                                  @NonNull PlaybackRequest request) {
        session.putResolvedRequest(resolveSourceId(song), request);
        updateSearchSongFromRequestLocked(song, request);
    }

    @NonNull
    private String buildSearchTrackKey(@NonNull SearchTrack track) {
        return SEARCH_TRACK_SOURCE_PREFIX + track.getProviderId() + "|" + track.getTrackId();
    }

    private boolean isSearchQueueSong(@NonNull Song song) {
        return resolveSourceId(song).startsWith(SEARCH_TRACK_SOURCE_PREFIX);
    }

    @Nullable
    private String resolveWarmupPlayableSourceLocked(@NonNull Song song) {
        if (!isSearchQueueSong(song)) {
            return resolvePlayableSource(song);
        }
        if (!TextUtils.isEmpty(song.getAudioUrl())) {
            return song.getAudioUrl();
        }
        PlaybackRequest cachedRequest = resolveCachedSearchRequestLocked(resolveSourceId(song));
        if (cachedRequest == null) {
            return null;
        }
        return cachedRequest.getOriginalUrl();
    }

    @Nullable
    private PlaybackRequest buildWarmupPlaybackRequestLocked(@NonNull Song song) {
        String source = resolveWarmupPlayableSourceLocked(song);
        if (TextUtils.isEmpty(source)) {
            return null;
        }
        return new PlaybackRequest(resolveSourceId(song), source, song.isRadioStream());
    }

    private void scheduleSearchQueueExpansionIfNeededLocked() {
        if (searchQueueSession == null) {
            return;
        }
        int remaining = queue.size() - currentIndex - 1;
        if (remaining > SEARCH_QUEUE_EXPAND_THRESHOLD && queue.size() >= SEARCH_QUEUE_INITIAL_SIZE) {
            return;
        }
        int targetSize = Math.min(
                searchQueueSession.getMaxQueueSize(),
                Math.max(SEARCH_QUEUE_INITIAL_SIZE, currentIndex + 1 + SEARCH_QUEUE_INITIAL_SIZE));
        ensureSearchQueueCapacityLocked(targetSize);
    }

    private void ensureSearchQueueCapacityLocked(int targetQueueSize) {
        if (searchQueueSession == null) {
            return;
        }
        if (queue.size() >= targetQueueSize || queue.size() >= searchQueueSession.getMaxQueueSize()) {
            return;
        }
        if (searchQueueExpansionInFlight) {
            return;
        }
        long sessionGeneration = searchQueueSessionGeneration;
        searchQueueExpansionInFlight = true;
        searchQueueExecutor.execute(() -> expandSearchQueue(sessionGeneration, targetQueueSize));
    }

    private void scheduleSearchWarmupResolutionLocked(@NonNull Song song) {
        String sourceId = resolveSourceId(song);
        if (searchQueueSession == null) {
            return;
        }
        if (searchWarmupResolveInFlight) {
            return;
        }
        SearchTrack searchTrack = searchQueueSession.findTrack(sourceId);
        if (searchTrack == null) {
            return;
        }
        long sessionGeneration = searchQueueSessionGeneration;
        searchWarmupResolveInFlight = true;
        activeSearchWarmupResolveKey = sourceId;
        Log.d(TAG, "schedule search warmup resolve sourceId=" + sourceId
                + " title=" + song.getTitle());
        searchWarmupResolveExecutor.execute(() -> resolveSearchWarmupCandidate(sessionGeneration, sourceId, searchTrack));
    }

    private void resolveSearchWarmupCandidate(long sessionGeneration,
                                              @NonNull String sourceId,
                                              @NonNull SearchTrack searchTrack) {
        try {
            PlaybackRequest playbackRequest = playTrackFromSearchUseCase.execute(searchTrack);
            synchronized (this) {
                if (!isSearchQueueSessionActiveLocked(sessionGeneration)) {
                    return;
                }
                if (!sourceId.equals(activeSearchWarmupResolveKey)) {
                    return;
                }
                if (searchQueueSession != null) {
                    searchQueueSession.putResolvedRequest(sourceId, playbackRequest);
                }
                Song queuedSong = findQueuedSongBySourceIdLocked(sourceId);
                if (queuedSong != null) {
                    updateSearchSongFromRequestLocked(queuedSong, playbackRequest);
                }
                searchWarmupResolveInFlight = false;
                activeSearchWarmupResolveKey = "";
                Log.d(TAG, "search warmup resolved sourceId=" + sourceId
                        + " url=" + playbackRequest.getOriginalUrl());
                scheduleWarmupLocked();
            }
        } catch (IOException ioException) {
            synchronized (this) {
                if (isSearchQueueSessionActiveLocked(sessionGeneration)
                        && sourceId.equals(activeSearchWarmupResolveKey)) {
                    searchWarmupResolveInFlight = false;
                    activeSearchWarmupResolveKey = "";
                }
            }
            Log.e(TAG, "search warmup resolve failed sourceId=" + sourceId, ioException);
        }
    }

    private void expandSearchQueue(long sessionGeneration, int targetQueueSize) {
        boolean queueChanged = false;
        try {
            while (true) {
                SearchQueueExpansionPlan expansionPlan;
                synchronized (this) {
                    if (!isSearchQueueSessionActiveLocked(sessionGeneration) || searchQueueSession == null) {
                        return;
                    }
                    expansionPlan = searchQueueSession.createExpansionPlan(queue.size(), targetQueueSize);
                    if (expansionPlan.tracksToAppend.isEmpty()
                            && !expansionPlan.shouldFetchMore
                            && !expansionPlan.needsMoreCapacity) {
                        return;
                    }
                    if (!expansionPlan.tracksToAppend.isEmpty()) {
                        queue.addAll(buildSearchSongs(expansionPlan.tracksToAppend));
                        queueChanged = true;
                        Log.d(TAG, "expandSearchQueue appendLoaded count=" + expansionPlan.tracksToAppend.size()
                                + " queueSize=" + queue.size());
                        onQueueTopologyChangedLocked();
                        dispatchState();
                        continue;
                    }
                }

                SearchResultPage nextPage = searchUseCase.execute(new SearchQuery(
                        expansionPlan.keyword,
                        new SearchFilter(expansionPlan.scope, expansionPlan.page, expansionPlan.pageSize)));

                synchronized (this) {
                    if (!isSearchQueueSessionActiveLocked(sessionGeneration) || searchQueueSession == null) {
                        return;
                    }
                    searchQueueSession.absorbPage(nextPage);
                    Log.d(TAG, "expandSearchQueue pageLoaded page=" + nextPage.getFilter().getPage()
                            + " addedTracks=" + nextPage.getTracks().size()
                            + " hasMore=" + nextPage.isHasMore());
                }
            }
        } catch (IOException ioException) {
            Log.e(TAG, "expandSearchQueue failed", ioException);
        } finally {
            synchronized (this) {
                if (isSearchQueueSessionActiveLocked(sessionGeneration)) {
                    searchQueueExpansionInFlight = false;
                    if (queueChanged) {
                        dispatchState();
                    }
                }
            }
        }
    }

    private boolean isSearchQueueSessionActiveLocked(long sessionGeneration) {
        return searchQueueSession != null && searchQueueSessionGeneration == sessionGeneration;
    }

    private void clearSearchQueueSessionLocked() {
        searchQueueSession = null;
        searchQueueSessionGeneration++;
        searchQueueExpansionInFlight = false;
        searchWarmupResolveInFlight = false;
        activeSearchWarmupResolveKey = "";
    }

    @Nullable
    private Song findQueuedSongBySourceIdLocked(@NonNull String sourceId) {
        for (Song queuedSong : queue) {
            if (sourceId.equals(resolveSourceId(queuedSong))) {
                return queuedSong;
            }
        }
        return null;
    }

    private static final class SearchQueueExpansionPlan {
        private final List<SearchTrack> tracksToAppend;
        private final boolean shouldFetchMore;
        private final boolean needsMoreCapacity;
        private final String keyword;
        private final SearchScope scope;
        private final int page;
        private final int pageSize;

        private SearchQueueExpansionPlan(@NonNull List<SearchTrack> tracksToAppend,
                                         boolean shouldFetchMore,
                                         boolean needsMoreCapacity,
                                         @NonNull String keyword,
                                         @NonNull SearchScope scope,
                                         int page,
                                         int pageSize) {
            this.tracksToAppend = tracksToAppend;
            this.shouldFetchMore = shouldFetchMore;
            this.needsMoreCapacity = needsMoreCapacity;
            this.keyword = keyword;
            this.scope = scope;
            this.page = page;
            this.pageSize = pageSize;
        }
    }

    private static final class SearchQueueSession {
        private final String keyword;
        private final SearchScope scope;
        private final int pageSize;
        private final int maxQueueSize;
        private final List<SearchTrack> loadedTracks = new ArrayList<>();
        private final Set<String> loadedTrackKeys = new LinkedHashSet<>();
        private final java.util.Map<String, SearchTrack> trackMap = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, PlaybackRequest> resolvedRequestMap = new java.util.LinkedHashMap<>();
        private boolean hasMore;
        private int nextPage;
        private int enqueuedTrackCount;

        private SearchQueueSession(@NonNull SearchResultPage resultPage) {
            keyword = resultPage.getKeyword();
            scope = resultPage.getFilter().getScope();
            pageSize = Math.max(1, resultPage.getFilter().getPageSize());
            maxQueueSize = Math.min(
                    SEARCH_QUEUE_MAX_SIZE,
                    resultPage.getTotalCount() > 0 ? resultPage.getTotalCount() : SEARCH_QUEUE_MAX_SIZE);
            absorbPage(resultPage);
        }

        private int getMaxQueueSize() {
            return maxQueueSize;
        }

        private void setEnqueuedTrackCount(int enqueuedTrackCount) {
            this.enqueuedTrackCount = Math.max(0, Math.min(enqueuedTrackCount, loadedTracks.size()));
        }

        @Nullable
        private SearchTrack findTrack(@NonNull String searchKey) {
            return trackMap.get(searchKey);
        }

        @Nullable
        private PlaybackRequest getResolvedRequest(@NonNull String searchKey) {
            return resolvedRequestMap.get(searchKey);
        }

        private void putResolvedRequest(@NonNull String searchKey, @NonNull PlaybackRequest playbackRequest) {
            resolvedRequestMap.put(searchKey, playbackRequest);
        }

        private void absorbPage(@NonNull SearchResultPage page) {
            for (SearchTrack track : page.getTracks()) {
                String searchKey = SEARCH_TRACK_SOURCE_PREFIX + track.getProviderId() + "|" + track.getTrackId();
                if (!loadedTrackKeys.add(searchKey)) {
                    continue;
                }
                loadedTracks.add(track);
                trackMap.put(searchKey, track);
            }
            hasMore = page.isHasMore();
            nextPage = page.getFilter().getPage() + 1;
        }

        @NonNull
        private SearchQueueExpansionPlan createExpansionPlan(int currentQueueSize, int targetQueueSize) {
            int boundedTarget = Math.min(maxQueueSize, Math.max(currentQueueSize, targetQueueSize));
            if (currentQueueSize >= boundedTarget) {
                return new SearchQueueExpansionPlan(
                        java.util.Collections.emptyList(),
                        false,
                        false,
                        keyword,
                        scope,
                        nextPage,
                        pageSize);
            }
            int appendUntil = Math.min(Math.min(loadedTracks.size(), boundedTarget), maxQueueSize);
            if (enqueuedTrackCount < appendUntil) {
                List<SearchTrack> tracksToAppend = new ArrayList<>();
                for (int index = enqueuedTrackCount; index < appendUntil; index++) {
                    tracksToAppend.add(loadedTracks.get(index));
                }
                enqueuedTrackCount = appendUntil;
                return new SearchQueueExpansionPlan(
                        tracksToAppend,
                        false,
                        true,
                        keyword,
                        scope,
                        nextPage,
                        pageSize);
            }
            if (!hasMore || loadedTracks.size() >= maxQueueSize) {
                return new SearchQueueExpansionPlan(
                        java.util.Collections.emptyList(),
                        false,
                        false,
                        keyword,
                        scope,
                        nextPage,
                        pageSize);
            }
            return new SearchQueueExpansionPlan(
                    java.util.Collections.emptyList(),
                    true,
                    true,
                    keyword,
                    scope,
                    nextPage,
                    pageSize);
        }
    }
}
