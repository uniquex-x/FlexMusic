package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.io.IOException;

public final class SearchUseCase {

    private final ISearchRepository searchRepository;

    public SearchUseCase(@NonNull ISearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @NonNull
    public SearchResultPage execute(@NonNull SearchQuery query) throws IOException {
        return searchRepository.search(query);
    }
}
