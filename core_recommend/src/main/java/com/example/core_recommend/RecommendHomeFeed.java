package com.example.core_recommend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecommendHomeFeed {

    private final RecommendCategory defaultCategory;
    private final List<RecommendSection> sections;
    private final List<RecommendBrowseCategory> browseCategories;

    public RecommendHomeFeed(@NonNull RecommendCategory defaultCategory,
                             @NonNull List<RecommendSection> sections,
                             @NonNull List<RecommendBrowseCategory> browseCategories) {
        this.defaultCategory = defaultCategory;
        this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        this.browseCategories = Collections.unmodifiableList(new ArrayList<>(browseCategories));
    }

    @NonNull
    public RecommendCategory getDefaultCategory() {
        return defaultCategory;
    }

    @NonNull
    public List<RecommendSection> getSections() {
        return sections;
    }

    @NonNull
    public List<RecommendBrowseCategory> getBrowseCategories() {
        return browseCategories;
    }

    @Nullable
    public RecommendSection findSection(@NonNull RecommendCategory category) {
        for (RecommendSection section : sections) {
            if (section.getCategory() == category) {
                return section;
            }
        }
        return null;
    }
}
