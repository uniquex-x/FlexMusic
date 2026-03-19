package com.example.core_data.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;

import java.util.LinkedHashMap;
import java.util.Map;

public class SearchCacheStore {

    private static final long CACHE_TTL_MS = 60 * 1000L;

    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();

    public synchronized void put(@NonNull SearchQuery query, @NonNull SearchResultPage page) {
        cache.put(buildKey(query), new CacheEntry(page, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    @Nullable
    public synchronized SearchResultPage get(@NonNull SearchQuery query) {
        CacheEntry entry = cache.get(buildKey(query));
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMs <= System.currentTimeMillis()) {
            cache.remove(buildKey(query));
            return null;
        }
        return entry.page;
    }

    @NonNull
    private String buildKey(@NonNull SearchQuery query) {
        return query.getKeyword()
                + "|"
                + query.getFilter().getScope()
                + "|"
                + query.getFilter().getPage()
                + "|"
                + query.getFilter().getPageSize();
    }

    private static final class CacheEntry {
        private final SearchResultPage page;
        private final long expiresAtMs;

        private CacheEntry(@NonNull SearchResultPage page, long expiresAtMs) {
            this.page = page;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
