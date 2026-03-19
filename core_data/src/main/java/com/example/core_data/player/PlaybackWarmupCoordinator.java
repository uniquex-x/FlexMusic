package com.example.core_data.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.player.PlaybackWarmupLevel;
import com.example.core_domain.player.PlaybackWarmupRequest;

import java.util.List;

public final class PlaybackWarmupCoordinator {

    @Nullable
    public PlaybackWarmupRequest planWarmup(@NonNull List<PlaybackRequest> queue,
                                            int currentIndex,
                                            int nextIndex) {
        if (queue.isEmpty() || currentIndex < 0 || currentIndex >= queue.size()) {
            return null;
        }

        if (nextIndex >= 0 && nextIndex < queue.size() && nextIndex != currentIndex) {
            PlaybackRequest nextRequest = queue.get(nextIndex);
            return new PlaybackWarmupRequest(
                    nextRequest.getSourceId(),
                    nextRequest.getOriginalUrl(),
                    nextRequest.isLiveStream(),
                    nextRequest.isLiveStream()
                            ? PlaybackWarmupLevel.URL_METADATA
                            : PlaybackWarmupLevel.PLAYBACK_CANDIDATE);
        }

        PlaybackRequest currentRequest = queue.get(currentIndex);
        return new PlaybackWarmupRequest(
                currentRequest.getSourceId(),
                currentRequest.getOriginalUrl(),
                currentRequest.isLiveStream(),
                PlaybackWarmupLevel.HOST);
    }
}
