package com.example.core_network.search.dto;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MusicSearchPageDto {

    private final int page;
    private final int pageSize;
    private final boolean hasMore;
    private final int totalCount;
    private final List<MusicSearchTrackDto> tracks;

    public MusicSearchPageDto(int page,
                              int pageSize,
                              boolean hasMore,
                              int totalCount,
                              @NonNull List<MusicSearchTrackDto> tracks) {
        this.page = Math.max(1, page);
        this.pageSize = Math.max(1, pageSize);
        this.hasMore = hasMore;
        this.totalCount = Math.max(0, totalCount);
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public int getTotalCount() {
        return totalCount;
    }

    @NonNull
    public List<MusicSearchTrackDto> getTracks() {
        return tracks;
    }
}
