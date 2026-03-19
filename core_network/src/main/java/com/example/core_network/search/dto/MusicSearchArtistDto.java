package com.example.core_network.search.dto;

import androidx.annotation.NonNull;

public final class MusicSearchArtistDto {

    private final String artistId;
    private final String providerId;
    private final String name;
    private final String coverUrl;

    public MusicSearchArtistDto(@NonNull String artistId,
                                @NonNull String providerId,
                                @NonNull String name,
                                @NonNull String coverUrl) {
        this.artistId = artistId;
        this.providerId = providerId;
        this.name = name;
        this.coverUrl = coverUrl;
    }

    @NonNull
    public String getArtistId() {
        return artistId;
    }

    @NonNull
    public String getProviderId() {
        return providerId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getCoverUrl() {
        return coverUrl;
    }
}
