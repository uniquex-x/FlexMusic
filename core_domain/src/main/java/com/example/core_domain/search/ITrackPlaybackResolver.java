package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

/**
 * @brief Repository contract that converts a track identity into playable candidates.
 *
 * Implementations hide provider-specific playback resolution, fallback and
 * cache policy from the feature layer while preserving the existing player pipeline.
 */
public interface ITrackPlaybackResolver {

    /**
     * @brief Resolve a search track playback intent into ordered playback candidates.
     * @param playbackIntent Provider-neutral playback intent emitted by the search result model.
     * @return Ordered playback candidates, best candidate first.
     * @throws IOException When no playable candidate can be resolved from the current provider.
     */
    @NonNull
    List<TrackPlaybackCandidate> resolveCandidates(@NonNull TrackPlaybackIntent playbackIntent) throws IOException;
}
