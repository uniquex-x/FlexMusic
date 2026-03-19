package com.example.core_data.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryStore {

    private static final String PREFS_NAME = "flexmusic_search_history";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY_COUNT = 12;

    private final SharedPreferences sharedPreferences;

    public SearchHistoryStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void recordQuery(@NonNull String keyword) {
        String trimmedKeyword = keyword.trim();
        if (TextUtils.isEmpty(trimmedKeyword)) {
            return;
        }
        List<String> history = readHistory();
        history.remove(trimmedKeyword);
        history.add(0, trimmedKeyword);
        while (history.size() > MAX_HISTORY_COUNT) {
            history.remove(history.size() - 1);
        }
        sharedPreferences.edit().putString(KEY_HISTORY, encodeHistory(history)).apply();
    }

    @NonNull
    public synchronized List<String> readHistory() {
        String encodedHistory = sharedPreferences.getString(KEY_HISTORY, "[]");
        List<String> history = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(encodedHistory);
            for (int i = 0; i < jsonArray.length(); i++) {
                String item = jsonArray.optString(i);
                if (!TextUtils.isEmpty(item)) {
                    history.add(item);
                }
            }
        } catch (JSONException ignored) {
        }
        return history;
    }

    @NonNull
    private String encodeHistory(@NonNull List<String> history) {
        JSONArray jsonArray = new JSONArray();
        for (String item : history) {
            jsonArray.put(item);
        }
        return jsonArray.toString();
    }
}
