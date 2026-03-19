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
    private final List<MusicSearchAlbumDto> albums;
    private final List<MusicSearchArtistDto> artists;
    private final List<MusicSearchPlaylistDto> playlists;

    public MusicSearchPageDto(int page,
                              int pageSize,
                              boolean hasMore,
                              int totalCount,
                              @NonNull List<MusicSearchTrackDto> tracks,
                              @NonNull List<MusicSearchAlbumDto> albums,
                              @NonNull List<MusicSearchArtistDto> artists,
                              @NonNull List<MusicSearchPlaylistDto> playlists) {
        this.page = Math.max(1, page);
        this.pageSize = Math.max(1, pageSize);
        this.hasMore = hasMore;
        this.totalCount = Math.max(0, totalCount);
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        this.albums = Collections.unmodifiableList(new ArrayList<>(albums));
        this.artists = Collections.unmodifiableList(new ArrayList<>(artists));
        this.playlists = Collections.unmodifiableList(new ArrayList<>(playlists));
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

    @NonNull
    public List<MusicSearchAlbumDto> getAlbums() {
        return albums;
    }

    @NonNull
    public List<MusicSearchArtistDto> getArtists() {
        return artists;
    }

    @NonNull
    public List<MusicSearchPlaylistDto> getPlaylists() {
        return playlists;
    }
}
