package com.example.core_recommend;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public final class RecommendCard {

    private final String id;
    private final int backgroundColor;
    private final int titleColor;
    private final int subtitleColor;

    public RecommendCard(@NonNull String id,
                         @ColorInt int backgroundColor,
                         @ColorInt int titleColor,
                         @ColorInt int subtitleColor) {
        this.id = id;
        this.backgroundColor = backgroundColor;
        this.titleColor = titleColor;
        this.subtitleColor = subtitleColor;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @ColorInt
    public int getBackgroundColor() {
        return backgroundColor;
    }

    @ColorInt
    public int getTitleColor() {
        return titleColor;
    }

    @ColorInt
    public int getSubtitleColor() {
        return subtitleColor;
    }
}
