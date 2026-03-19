package com.example.core_domain.search;

import androidx.annotation.NonNull;

public final class SearchQuery {

    private final String keyword;
    private final SearchFilter filter;

    public SearchQuery(@NonNull String keyword) {
        this(keyword, SearchFilter.DEFAULT_TRACK_FILTER);
    }

    public SearchQuery(@NonNull String keyword, @NonNull SearchFilter filter) {
        this.keyword = keyword.trim();
        this.filter = filter;
    }

    @NonNull
    public String getKeyword() {
        return keyword;
    }

    @NonNull
    public SearchFilter getFilter() {
        return filter;
    }
}
