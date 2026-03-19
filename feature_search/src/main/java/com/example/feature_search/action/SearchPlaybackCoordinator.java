package com.example.feature_search.action;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.PlayTrackFromSearchUseCase;
import com.example.core_domain.search.SearchTrack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class SearchPlaybackCoordinator {

    private static final String TAG = "SearchPlaybackCoord";

    /**
     * @brief Callback used to deliver asynchronous playback-intent resolution results.
     *
     * Implementors are notified on the main thread after the coordinator finishes
     * resolving a selected search track into a normalized playback request.
     */
    public interface IListener {
        /**
         * @brief Called when playback resolution succeeds.
         * @param track Search track associated with the resolved playback request.
         * @param playbackRequest Normalized playback request ready for the player pipeline.
         */
        void onPlaybackResolved(@NonNull SearchTrack track, @NonNull PlaybackRequest playbackRequest);

        /**
         * @brief Called when playback resolution fails.
         * @param track Search track whose playback request could not be resolved.
         * @param exception Resolution failure with provider or cache context.
         */
        void onPlaybackResolveFailed(@NonNull SearchTrack track, @NonNull IOException exception);
    }

    /**
     * @brief Callback used for queue-level search playback resolution.
     */
    public interface IQueueListener {
        /**
         * @brief Called when at least one track in the queue resolves successfully.
         * @param tracks Ordered tracks that resolved successfully.
         * @param playbackRequests Ordered playback requests matching the track list.
         * @param skippedCount Number of queue entries skipped because resolution failed.
         */
        void onQueueResolved(@NonNull List<SearchTrack> tracks,
                             @NonNull List<PlaybackRequest> playbackRequests,
                             int skippedCount);

        /**
         * @brief Called when no queue item could be resolved.
         * @param exception Failure explaining why queue playback could not start.
         */
        void onQueueResolveFailed(@NonNull IOException exception);
    }

    private final PlayTrackFromSearchUseCase playTrackFromSearchUseCase;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public SearchPlaybackCoordinator(@NonNull PlayTrackFromSearchUseCase playTrackFromSearchUseCase,
                                     @NonNull ExecutorService executorService,
                                     @NonNull Handler mainHandler) {
        this.playTrackFromSearchUseCase = playTrackFromSearchUseCase;
        this.executorService = executorService;
        this.mainHandler = mainHandler;
    }

    public void play(@NonNull SearchTrack track, @NonNull IListener listener) {
        executorService.execute(() -> {
            try {
                PlaybackRequest playbackRequest = playTrackFromSearchUseCase.execute(track);
                mainHandler.post(() -> listener.onPlaybackResolved(track, playbackRequest));
            } catch (IOException ioException) {
                mainHandler.post(() -> listener.onPlaybackResolveFailed(track, ioException));
            }
        });
    }

    public void resolveQueue(@NonNull List<SearchTrack> tracks, @NonNull IQueueListener listener) {
        executorService.execute(() -> {
            List<SearchTrack> resolvedTracks = new ArrayList<>();
            List<PlaybackRequest> playbackRequests = new ArrayList<>();
            int skippedCount = 0;
            IOException terminalException = null;
            for (SearchTrack track : tracks) {
                try {
                    playbackRequests.add(playTrackFromSearchUseCase.execute(track));
                    resolvedTracks.add(track);
                } catch (IOException ioException) {
                    skippedCount++;
                    terminalException = ioException;
                    Log.e(TAG, "resolveQueue failed trackId=" + track.getTrackId(), ioException);
                }
            }
            IOException finalTerminalException = terminalException;
            int finalSkippedCount = skippedCount;
            if (resolvedTracks.isEmpty()) {
                mainHandler.post(() -> listener.onQueueResolveFailed(
                        finalTerminalException == null
                                ? new IOException("No playable tracks resolved")
                                : finalTerminalException));
                return;
            }
            mainHandler.post(() -> listener.onQueueResolved(resolvedTracks, playbackRequests, finalSkippedCount));
        });
    }
}
