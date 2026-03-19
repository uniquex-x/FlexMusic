package com.example.feature_search;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.SearchTrack;

import java.util.List;

/**
 * @brief Host callback contract exposed by the search feature module.
 *
 * The host activity implements this interface to receive a resolved playback
 * request together with the selected track metadata, then forwards the request
 * into the app-level playback controller.
 */
public interface ISearchHost {

    /**
     * @brief Notify the host that a search track is ready to enter the playback pipeline.
     * @param track Selected search track metadata for queue and UI display.
     * @param playbackRequest Normalized playback request ready for source resolution.
     */
    void onSearchPlaybackRequested(@NonNull SearchTrack track, @NonNull PlaybackRequest playbackRequest);

    /**
     * @brief Notify the host that a search track should be inserted as the next queue item.
     * @param track Selected search track metadata.
     * @param playbackRequest Normalized playback request ready for queue insertion.
     */
    void onSearchPlayNextRequested(@NonNull SearchTrack track, @NonNull PlaybackRequest playbackRequest);

    /**
     * @brief Notify the host that a search track should be appended to the playback queue.
     * @param track Selected search track metadata.
     * @param playbackRequest Normalized playback request ready for queue insertion.
     */
    void onSearchAddToQueueRequested(@NonNull SearchTrack track, @NonNull PlaybackRequest playbackRequest);

    /**
     * @brief Notify the host that a fully resolved search queue should replace the current queue.
     * @param tracks Ordered resolved track metadata.
     * @param playbackRequests Ordered playback requests matching the track list.
     * @param startIndex Queue index that should start playing immediately.
     */
    void onSearchQueuePlaybackRequested(@NonNull List<SearchTrack> tracks,
                                        @NonNull List<PlaybackRequest> playbackRequests,
                                        int startIndex);
}
