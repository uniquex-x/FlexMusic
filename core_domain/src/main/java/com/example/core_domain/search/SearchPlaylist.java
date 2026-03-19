package com.example.core_domain.search;

import androidx.annotation.NonNull;

public final class SearchPlaylist {

    private final String playlistId;
    private final String providerId;
    private final String title;
    private final String creatorName;
    private final String coverUrl;
    private final int trackCount;

    public SearchPlaylist(@NonNull String playlistId,
                          @NonNull String providerId,
                          @NonNull String title,
                          @NonNull String creatorName,
                          @NonNull String coverUrl,
                          int trackCount) {
        this.playlistId = playlistId;
        this.providerId = providerId;
        this.title = title;
        this.creatorName = creatorName;
        this.coverUrl = coverUrl;
        this.trackCount = Math.max(0, trackCount);
    }

    @NonNull
    public String getPlaylistId() {
        return playlistId;
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
    public String getCreatorName() {
        return creatorName;
    }

    @NonNull
    public String getCoverUrl() {
        return coverUrl;
    }

    public int getTrackCount() {
        return trackCount;
    }
}
