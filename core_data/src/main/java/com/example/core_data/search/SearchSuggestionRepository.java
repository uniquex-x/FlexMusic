package com.example.core_data.search;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.search.ISearchSuggestionRepository;
import com.example.core_network.search.MusicSearchService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchSuggestionRepository implements ISearchSuggestionRepository {

    private static final String TAG = "SearchSuggestionRepo";
    private static final List<String> FALLBACK_HOT_SEARCHES = Arrays.asList(
            "Chill",
            "Acoustic",
            "Ambient",
            "Piano",
            "Workout",
            "Jazz");

    private final SearchHistoryStore searchHistoryStore;
    private final MusicSearchService musicSearchService;

    public SearchSuggestionRepository(@NonNull SearchHistoryStore searchHistoryStore) {
        this(searchHistoryStore, new MusicSearchService());
    }

    public SearchSuggestionRepository(@NonNull SearchHistoryStore searchHistoryStore,
                                      @NonNull MusicSearchService musicSearchService) {
        this.searchHistoryStore = searchHistoryStore;
        this.musicSearchService = musicSearchService;
    }

    @NonNull
    @Override
    public List<String> suggest(@NonNull String keyword) throws IOException {
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<String> history = searchHistoryStore.readHistory();
        if (normalizedKeyword.isEmpty()) {
            return history;
        }
        Set<String> suggestions = new LinkedHashSet<>();
        for (String item : history) {
            String normalizedItem = item.toLowerCase(Locale.ROOT);
            if (normalizedItem.startsWith(normalizedKeyword) || normalizedItem.contains(normalizedKeyword)) {
                suggestions.add(item);
            }
        }
        try {
            suggestions.addAll(musicSearchService.loadAutocompleteSuggestions(keyword, 8));
        } catch (IOException ioException) {
            Log.e(TAG, "suggest remote load failed keyword=" + keyword, ioException);
            if (suggestions.isEmpty()) {
                throw ioException;
            }
        }
        return new ArrayList<>(suggestions);
    }

    @Override
    public void recordQuery(@NonNull String keyword) {
        searchHistoryStore.recordQuery(keyword);
    }

    @NonNull
    @Override
    public List<String> getHistory() {
        return searchHistoryStore.readHistory();
    }

    @NonNull
    @Override
    public List<String> getHotSearches() throws IOException {
        try {
            List<String> hotSearches = musicSearchService.loadTrendingKeywords(8);
            if (!hotSearches.isEmpty()) {
                return hotSearches;
            }
        } catch (IOException ioException) {
            Log.e(TAG, "getHotSearches remote load failed", ioException);
        }
        return new ArrayList<>(FALLBACK_HOT_SEARCHES);
    }

    @Override
    public void clearHistory() {
        searchHistoryStore.clearHistory();
    }
}
