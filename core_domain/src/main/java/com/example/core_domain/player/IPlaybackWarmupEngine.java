package com.example.core_domain.player;

import androidx.annotation.NonNull;

/**
 * @brief Best-effort warmup engine for future playback candidates.
 *
 * Implementations may pre-connect to the target host, resolve redirect and metadata, create
 * localhost proxy sessions, or prime a small head cache. Warmup must stay asynchronous-friendly:
 * failure to warm a candidate must never be required for the normal cold playback path to work.
 */
public interface IPlaybackWarmupEngine {

    /**
     * @brief Executes warmup for a known playback candidate.
     * @param request Description of the candidate to warm and the maximum warmup level to reach.
     *                Must not be null.
     * @return A snapshot describing the highest warmup level actually reached.
     */
    @NonNull
    PlaybackWarmupSnapshot warmup(@NonNull PlaybackWarmupRequest request);

    /**
     * @brief Cancels and releases any prepared warmup resources for one candidate.
     * @param sourceId Stable source identifier for the warmed candidate. Must not be null.
     */
    void cancelWarmup(@NonNull String sourceId);

    /**
     * @brief Cancels and releases all prepared warmup resources managed by this engine.
     */
    void cancelAllWarmups();
}
