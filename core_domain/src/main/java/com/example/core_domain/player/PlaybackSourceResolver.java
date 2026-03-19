package com.example.core_domain.player;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * @brief Resolves a business-level playback request into a player-ready source.
 *
 * Implementations may normalize local paths, create localhost proxy sessions for seekable
 * network streams, or reuse previously prepared playback candidates. Callers should treat this
 * interface as the only entry point for turning a logical track selection into a concrete URL or
 * file-backed source that the player kernel can open.
 */
public interface PlaybackSourceResolver {

    /**
     * @brief Resolves a playback request into a concrete playable source.
     * @param request Input request describing the selected media source. Must not be null.
     * @return A fully resolved playable source that can be handed to the player kernel.
     * @throws IOException When the source cannot be resolved or prepared for playback.
     */
    @NonNull
    ResolvedPlayableSource resolve(@NonNull PlaybackRequest request) throws IOException;
}
