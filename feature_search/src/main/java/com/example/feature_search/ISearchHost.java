package com.example.feature_search;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchTrack;

/**
 * @brief Host callback contract exposed by the search feature module.
 *
 * The host activity implements this interface to receive a resolved playback
 * request together with the current search-result context, then forwards the
 * request into the app-level playback controller.
 */
public interface ISearchHost {

    /**
     * @brief Notify the host that a search result should start playback from the active result set.
     * @param resultPage Current search result page snapshot used to seed the playback queue.
     * @param startIndex Queue index that should start playing immediately.
     * @param playbackRequest Resolved playback request for the selected start item.
     */
    void onSearchPlaybackRequested(@NonNull SearchResultPage resultPage,
                                   int startIndex,
                                   @NonNull PlaybackRequest playbackRequest);

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
     * @brief Notify the host that the active search-result queue should replace the current queue.
     * @param resultPage Current search result page snapshot used to seed the playback queue.
     * @param startIndex Queue index that should start playing immediately.
     * @param playbackRequest Resolved playback request for the selected start item.
     */
    void onSearchQueuePlaybackRequested(@NonNull SearchResultPage resultPage,
                                        int startIndex,
                                        @NonNull PlaybackRequest playbackRequest);
}
