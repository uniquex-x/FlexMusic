package com.example.core_domain.search;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchResultPage {

    private final String keyword;
    private final SearchFilter filter;
    private final boolean hasMore;
    private final int totalCount;
    private final List<SearchTrack> tracks;
    private final List<SearchAlbum> albums;
    private final List<SearchArtist> artists;
    private final List<SearchPlaylist> playlists;

    public SearchResultPage(@NonNull String keyword,
                            @NonNull SearchFilter filter,
                            boolean hasMore,
                            int totalCount,
                            @NonNull List<SearchTrack> tracks,
                            @NonNull List<SearchAlbum> albums,
                            @NonNull List<SearchArtist> artists,
                            @NonNull List<SearchPlaylist> playlists) {
        this.keyword = keyword;
        this.filter = filter;
        this.hasMore = hasMore;
        this.totalCount = Math.max(0, totalCount);
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        this.albums = Collections.unmodifiableList(new ArrayList<>(albums));
        this.artists = Collections.unmodifiableList(new ArrayList<>(artists));
        this.playlists = Collections.unmodifiableList(new ArrayList<>(playlists));
    }

    @NonNull
    public static SearchResultPage empty(@NonNull SearchQuery query) {
        return new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                false,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    public String getKeyword() {
        return keyword;
    }

    @NonNull
    public SearchFilter getFilter() {
        return filter;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public int getTotalCount() {
        return totalCount;
    }

    @NonNull
    public List<SearchTrack> getTracks() {
        return tracks;
    }

    @NonNull
    public List<SearchAlbum> getAlbums() {
        return albums;
    }

    @NonNull
    public List<SearchArtist> getArtists() {
        return artists;
    }

    @NonNull
    public List<SearchPlaylist> getPlaylists() {
        return playlists;
    }
}
