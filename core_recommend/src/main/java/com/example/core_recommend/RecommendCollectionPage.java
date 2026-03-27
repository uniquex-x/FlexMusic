package com.example.core_recommend;

import androidx.annotation.NonNull;

import com.example.core_domain.search.SearchTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecommendCollectionPage {

    private final String id;
    private final String title;
    private final String subtitle;
    private final boolean complete;
    private final List<SearchTrack> tracks;

    public RecommendCollectionPage(@NonNull String id,
                                   @NonNull String title,
                                   @NonNull String subtitle,
                                   boolean complete,
                                   @NonNull List<SearchTrack> tracks) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.complete = complete;
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
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

    public boolean isComplete() {
        return complete;
    }

    @NonNull
    public List<SearchTrack> getTracks() {
        return tracks;
    }
}
