package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * @brief Repository contract for normalized online catalog search.
 *
 * The implementation is responsible for accepting a domain-level query and
 * returning a provider-agnostic result page that can be rendered directly by
 * the search feature without exposing transport details.
 */
public interface ISearchRepository {

    /**
     * @brief Execute a normalized search request.
     * @param query Search keyword, scope, page and page size constraints.
     * @return A domain-level page containing tracks, albums, artists and playlists.
     * @throws IOException When the underlying remote or cached search data cannot be loaded.
     */
    @NonNull
    SearchResultPage search(@NonNull SearchQuery query) throws IOException;
}
