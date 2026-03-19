package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

public final class GetSearchSuggestionsUseCase {

    private final ISearchSuggestionRepository suggestionRepository;

    public GetSearchSuggestionsUseCase(@NonNull ISearchSuggestionRepository suggestionRepository) {
        this.suggestionRepository = suggestionRepository;
    }

    @NonNull
    public List<String> execute(@NonNull String keyword) throws IOException {
        return suggestionRepository.suggest(keyword);
    }

    public void record(@NonNull String keyword) {
        suggestionRepository.recordQuery(keyword);
    }

    @NonNull
    public List<String> getHistory() {
        return suggestionRepository.getHistory();
    }

    public void clearHistory() {
        suggestionRepository.clearHistory();
    }
}
