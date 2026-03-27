package com.example.core_recommend;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public final class RecommendBrowseCategory {

    private final String id;
    private final String title;
    private final String subtitle;
    private final int backgroundColor;

    public RecommendBrowseCategory(@NonNull String id,
                                   @NonNull String title,
                                   @NonNull String subtitle,
                                   @ColorInt int backgroundColor) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.backgroundColor = backgroundColor;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getSubtitle() {
        return subtitle;
    }

    @ColorInt
    public int getBackgroundColor() {
        return backgroundColor;
    }
}
