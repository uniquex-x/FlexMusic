package com.example.core_recommend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum RecommendCategory {
    DAILY_RECOMMEND("daily_recommend"),
    TRENDING("trending"),
    TOP_LIST("top_list");

    private final String id;

    RecommendCategory(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public static RecommendCategory fromId(@Nullable String id) {
        if (id == null) {
            return DAILY_RECOMMEND;
        }
        for (RecommendCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return DAILY_RECOMMEND;
    }
}
