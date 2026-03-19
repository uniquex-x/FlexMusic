package com.example.core_data.search;

import androidx.annotation.NonNull;

import com.example.core_domain.search.ISearchSuggestionRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchSuggestionRepository implements ISearchSuggestionRepository {

    private final SearchHistoryStore searchHistoryStore;

    public SearchSuggestionRepository(@NonNull SearchHistoryStore searchHistoryStore) {
        this.searchHistoryStore = searchHistoryStore;
    }

    @NonNull
    @Override
    public List<String> suggest(@NonNull String keyword) throws IOException {
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<String> history = searchHistoryStore.readHistory();
        if (normalizedKeyword.isEmpty()) {
            return history;
        }
        List<String> suggestions = new ArrayList<>();
        for (String item : history) {
            String normalizedItem = item.toLowerCase(Locale.ROOT);
            if (normalizedItem.startsWith(normalizedKeyword) || normalizedItem.contains(normalizedKeyword)) {
                suggestions.add(item);
            }
        }
        return suggestions;
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
}
