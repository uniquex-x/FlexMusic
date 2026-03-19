package com.example.core_domain.lyrics;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * @brief Repository contract for loading normalized song lyrics.
 *
 * Implementations may combine remote synced-lyrics providers, plain-text
 * lyric sources and local caches, but callers only depend on a single
 * normalized query/response pair.
 */
public interface ILyricsRepository {

    /**
     * @brief Resolve lyrics for the given track metadata.
     * @param query Stable track metadata used to match a lyrics record.
     * @return Normalized lyrics result. Implementations should return an empty
     *         result instead of null when no lyrics can be found.
     * @throws IOException When the configured lyrics source cannot be reached
     *         or its payload cannot be parsed.
     */
    @NonNull
    LyricsResult loadLyrics(@NonNull LyricsQuery query) throws IOException;
}
