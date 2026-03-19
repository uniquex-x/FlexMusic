package com.example.core_domain.search;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;

import java.io.IOException;
import java.util.List;

public final class PlayTrackFromSearchUseCase {

    private final ITrackPlaybackResolver trackPlaybackResolver;

    public PlayTrackFromSearchUseCase(@NonNull ITrackPlaybackResolver trackPlaybackResolver) {
        this.trackPlaybackResolver = trackPlaybackResolver;
    }

    @NonNull
    public PlaybackRequest execute(@NonNull SearchTrack searchTrack) throws IOException {
        List<TrackPlaybackCandidate> candidates =
                trackPlaybackResolver.resolveCandidates(searchTrack.getPlaybackIntent());
        long now = System.currentTimeMillis();
        TrackPlaybackCandidate selectedCandidate = null;
        for (TrackPlaybackCandidate candidate : candidates) {
            if (TextUtils.isEmpty(candidate.getOriginalUrl())) {
                continue;
            }
            if (candidate.getExpiresAtMs() > 0L && candidate.getExpiresAtMs() <= now) {
                continue;
            }
            if (selectedCandidate == null || candidate.getConfidence() > selectedCandidate.getConfidence()) {
                selectedCandidate = candidate;
            }
        }
        if (selectedCandidate == null) {
            throw new IOException("No playable source resolved for " + searchTrack.getTrackId());
        }
        return new PlaybackRequest(
                selectedCandidate.getSourceId(),
                selectedCandidate.getOriginalUrl(),
                selectedCandidate.isLive());
    }
}
