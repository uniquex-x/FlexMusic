package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

/**
 * @brief Repository contract for search suggestions and history recall.
 *
 * Implementations may combine local history, hot queries and remote suggestion
 * services, but callers only depend on a simple keyword-based API.
 */
public interface ISearchSuggestionRepository {

    /**
     * @brief Load ordered suggestion tokens for the current input.
     * @param keyword Current user input. Empty input should typically return history or trending suggestions.
     * @return Ordered suggestion strings ready for display.
     * @throws IOException When a remote suggestion source is configured and fails to load.
     */
    @NonNull
    List<String> suggest(@NonNull String keyword) throws IOException;

    /**
     * @brief Persist a successful search keyword into history.
     * @param keyword Final submitted search keyword. Empty values must be ignored by implementations.
     */
    void recordQuery(@NonNull String keyword);

    /**
     * @brief Read the stored local search history.
     * @return Ordered history entries, newest first.
     */
    @NonNull
    List<String> getHistory();
}
