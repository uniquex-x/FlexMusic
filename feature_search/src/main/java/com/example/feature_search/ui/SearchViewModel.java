package com.example.feature_search.ui;

import androidx.annotation.NonNull;

import com.example.core_domain.search.GetSearchSuggestionsUseCase;
import com.example.core_domain.search.SearchFilter;
import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchScope;
import com.example.core_domain.search.SearchUseCase;

import java.io.IOException;
import java.util.List;

public final class SearchViewModel {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SearchUseCase searchUseCase;
    private final GetSearchSuggestionsUseCase getSearchSuggestionsUseCase;

    public SearchViewModel(@NonNull SearchUseCase searchUseCase,
                           @NonNull GetSearchSuggestionsUseCase getSearchSuggestionsUseCase) {
        this.searchUseCase = searchUseCase;
        this.getSearchSuggestionsUseCase = getSearchSuggestionsUseCase;
    }

    @NonNull
    public SearchResultPage search(@NonNull String keyword,
                                   @NonNull SearchScope scope,
                                   int page) throws IOException {
        SearchResultPage resultPage = searchUseCase.execute(new SearchQuery(
                keyword,
                new SearchFilter(scope, page, DEFAULT_PAGE_SIZE)));
        if (page == SearchFilter.DEFAULT_PAGE) {
            getSearchSuggestionsUseCase.record(keyword);
        }
        return resultPage;
    }

    @NonNull
    public List<String> loadSuggestions(@NonNull String keyword) throws IOException {
        return getSearchSuggestionsUseCase.execute(keyword);
    }

    @NonNull
    public List<String> loadHistory() {
        return getSearchSuggestionsUseCase.getHistory();
    }

    @NonNull
    public List<String> loadHotSearches() throws IOException {
        return getSearchSuggestionsUseCase.getHotSearches();
    }

    public void clearHistory() {
        getSearchSuggestionsUseCase.clearHistory();
    }
}
