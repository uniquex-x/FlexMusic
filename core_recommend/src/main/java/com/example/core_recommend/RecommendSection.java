package com.example.core_recommend;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecommendSection {

    private final RecommendCategory category;
    private final List<RecommendCard> cards;

    public RecommendSection(@NonNull RecommendCategory category,
                            @NonNull List<RecommendCard> cards) {
        this.category = category;
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
    }

    @NonNull
    public RecommendCategory getCategory() {
        return category;
    }

    @NonNull
    public List<RecommendCard> getCards() {
        return cards;
    }
}
