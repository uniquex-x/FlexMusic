package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchAlbum {

    private final String albumId;
    private final String providerId;
    private final String title;
    private final List<String> artistNames;
    private final String coverUrl;
    private final int trackCount;

    public SearchAlbum(@NonNull String albumId,
                       @NonNull String providerId,
                       @NonNull String title,
                       @NonNull List<String> artistNames,
                       @NonNull String coverUrl,
                       int trackCount) {
        this.albumId = albumId;
        this.providerId = providerId;
        this.title = title;
        this.artistNames = Collections.unmodifiableList(new ArrayList<>(artistNames));
        this.coverUrl = coverUrl;
        this.trackCount = Math.max(0, trackCount);
    }

    @NonNull
    public String getAlbumId() {
        return albumId;
    }

    @NonNull
    public String getProviderId() {
        return providerId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public List<String> getArtistNames() {
        return artistNames;
    }

    @NonNull
    public String getCoverUrl() {
        return coverUrl;
    }

    public int getTrackCount() {
        return trackCount;
    }
}
