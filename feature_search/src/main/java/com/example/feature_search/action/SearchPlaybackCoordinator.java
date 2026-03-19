package com.example.feature_search.action;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.PlayTrackFromSearchUseCase;
import com.example.core_domain.search.SearchTrack;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public final class SearchPlaybackCoordinator {

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
}
