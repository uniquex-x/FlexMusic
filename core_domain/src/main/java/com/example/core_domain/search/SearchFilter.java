package com.example.core_domain.search;

import androidx.annotation.NonNull;

public final class SearchFilter {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final SearchFilter DEFAULT_TRACK_FILTER =
            new SearchFilter(SearchScope.TRACKS, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);

    private final SearchScope scope;
    private final int page;
    private final int pageSize;

    public SearchFilter(@NonNull SearchScope scope, int page, int pageSize) {
        this.scope = scope;
        this.page = Math.max(DEFAULT_PAGE, page);
        this.pageSize = Math.max(1, pageSize);
    }

    @NonNull
    public SearchScope getScope() {
        return scope;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
