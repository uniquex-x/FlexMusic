package com.example.feature_search;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.SearchTrack;

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
}
