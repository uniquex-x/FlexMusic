package com.example.core_data.search;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.search.ISearchRepository;
import com.example.core_domain.search.SearchAlbum;
import com.example.core_domain.search.SearchArtist;
import com.example.core_domain.search.SearchAvailability;
import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchScope;
import com.example.core_domain.search.SearchTrack;
import com.example.core_domain.search.TrackPlaybackIntent;
import com.example.core_network.search.MusicSearchService;
import com.example.core_network.search.SpotifySearchService;
import com.example.core_network.search.dto.MusicSearchAlbumDto;
import com.example.core_network.search.dto.MusicSearchArtistDto;
import com.example.core_network.search.dto.MusicSearchPageDto;
import com.example.core_network.search.dto.MusicSearchPlaylistDto;
import com.example.core_network.search.dto.MusicSearchTrackDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OnlineSearchRepository implements ISearchRepository {

    private static final String TAG = "OnlineSearchRepo";
    private static final int ALL_SCOPE_SECONDARY_PAGE_SIZE = 6;

    private final boolean spotifySearchEnabled;
    private final MusicSearchService musicSearchService;
    private final SpotifySearchService spotifySearchService;
    private final SearchCacheStore searchCacheStore;
    private final SearchResultDeduplicator searchResultDeduplicator;
    private final SearchResultRanker searchResultRanker;

    public OnlineSearchRepository() {
        this(true);
    }

    public OnlineSearchRepository(boolean spotifySearchEnabled) {
        this(spotifySearchEnabled, new MusicSearchService(), new SpotifySearchService(), new SearchCacheStore(),
                new SearchResultDeduplicator(), new SearchResultRanker());
    }

    public OnlineSearchRepository(boolean spotifySearchEnabled,
                                  @NonNull MusicSearchService musicSearchService,
                                  @NonNull SpotifySearchService spotifySearchService,
                                  @NonNull SearchCacheStore searchCacheStore,
                                  @NonNull SearchResultDeduplicator searchResultDeduplicator,
                                  @NonNull SearchResultRanker searchResultRanker) {
        this.spotifySearchEnabled = spotifySearchEnabled;
        this.musicSearchService = musicSearchService;
        this.spotifySearchService = spotifySearchService;
        this.searchCacheStore = searchCacheStore;
        this.searchResultDeduplicator = searchResultDeduplicator;
        this.searchResultRanker = searchResultRanker;
    }

    @NonNull
    @Override
    public SearchResultPage search(@NonNull SearchQuery query) throws IOException {
        if (TextUtils.isEmpty(query.getKeyword())) {
            return SearchResultPage.empty(query);
        }

        SearchResultPage cachedPage = searchCacheStore.get(query);
        if (cachedPage != null) {
            Log.d(TAG, "search cache hit keyword=" + query.getKeyword()
                    + " scope=" + query.getFilter().getScope()
                    + " page=" + query.getFilter().getPage());
            return cachedPage;
        }

        SearchResultPage resultPage;
        SearchScope scope = query.getFilter().getScope();
        switch (scope) {
            case ALBUMS:
                resultPage = mapAlbumPage(query, musicSearchService.searchAlbums(
                        query.getKeyword(),
                        query.getFilter().getPage(),
                        query.getFilter().getPageSize()));
                break;
            case ARTISTS:
                resultPage = mapArtistPage(query, musicSearchService.searchArtists(
                        query.getKeyword(),
                        query.getFilter().getPage(),
                        query.getFilter().getPageSize()));
                break;
            case PLAYLISTS:
                resultPage = mapPlaylistPage(query, musicSearchService.searchPlaylists(
                        query.getKeyword(),
                        query.getFilter().getPage(),
                        query.getFilter().getPageSize()));
                break;
            case ALL:
                resultPage = loadAllScopePage(query);
                break;
            case TRACKS:
            default:
                resultPage = mapTrackPage(query, loadTrackPage(query));
                break;
        }
        searchCacheStore.put(query, resultPage);
        Log.d(TAG, "search keyword=" + query.getKeyword()
                + " scope=" + scope
                + " tracks=" + resultPage.getTracks().size()
                + " albums=" + resultPage.getAlbums().size()
                + " artists=" + resultPage.getArtists().size()
                + " playlists=" + resultPage.getPlaylists().size()
                + " totalCount=" + resultPage.getTotalCount());
        return resultPage;
    }

    @NonNull
    private SearchResultPage loadAllScopePage(@NonNull SearchQuery query) throws IOException {
        int page = query.getFilter().getPage();
        int trackPageSize = query.getFilter().getPageSize();
        int secondaryPageSize = Math.max(1, Math.min(ALL_SCOPE_SECONDARY_PAGE_SIZE, trackPageSize));
        MusicSearchPageDto trackPageDto = loadTrackPage(query);
        MusicSearchPageDto albumPageDto = musicSearchService.searchAlbums(query.getKeyword(), page, secondaryPageSize);
        MusicSearchPageDto artistPageDto = musicSearchService.searchArtists(query.getKeyword(), page, secondaryPageSize);
        MusicSearchPageDto playlistPageDto = musicSearchService.searchPlaylists(query.getKeyword(), page, secondaryPageSize);

        List<SearchTrack> tracks = rankTracks(trackPageDto.getTracks(), query.getKeyword());
        List<SearchAlbum> albums = rankAlbums(albumPageDto.getAlbums(), query.getKeyword());
        List<SearchArtist> artists = rankArtists(artistPageDto.getArtists(), query.getKeyword());
        List<com.example.core_domain.search.SearchPlaylist> playlists =
                rankPlaylists(playlistPageDto.getPlaylists(), query.getKeyword());

        return new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                trackPageDto.isHasMore()
                        || albumPageDto.isHasMore()
                        || artistPageDto.isHasMore()
                        || playlistPageDto.isHasMore(),
                trackPageDto.getTotalCount()
                        + albumPageDto.getTotalCount()
                        + artistPageDto.getTotalCount()
                        + playlistPageDto.getTotalCount(),
                tracks,
                albums,
                artists,
                playlists);
    }

    @NonNull
    private MusicSearchPageDto loadTrackPage(@NonNull SearchQuery query) throws IOException {
        IOException spotifyError = null;
        IOException jamendoError = null;
        MusicSearchPageDto spotifyPageDto = emptyTrackPage(query);
        MusicSearchPageDto jamendoPageDto = emptyTrackPage(query);
        if (spotifySearchEnabled) {
            try {
                spotifyPageDto = spotifySearchService.searchTracks(
                        query.getKeyword(),
                        query.getFilter().getPage(),
                        query.getFilter().getPageSize());
            } catch (IOException ioException) {
                spotifyError = ioException;
                Log.w(TAG, "loadTrackPage spotify search failed keyword=" + query.getKeyword(), ioException);
            }
        } else {
            Log.d(TAG, "loadTrackPage spotify disabled keyword=" + query.getKeyword());
        }
        try {
            jamendoPageDto = musicSearchService.searchTracks(
                    query.getKeyword(),
                    query.getFilter().getPage(),
                    query.getFilter().getPageSize());
        } catch (IOException ioException) {
            jamendoError = ioException;
            Log.w(TAG, "loadTrackPage jamendo search failed keyword=" + query.getKeyword(), ioException);
        }
        if (spotifyError != null && jamendoError != null) {
            throw jamendoError;
        }
        List<MusicSearchTrackDto> mergedTracks = new ArrayList<>(spotifyPageDto.getTracks().size()
                + jamendoPageDto.getTracks().size());
        mergedTracks.addAll(spotifyPageDto.getTracks());
        mergedTracks.addAll(jamendoPageDto.getTracks());
        return new MusicSearchPageDto(
                query.getFilter().getPage(),
                Math.max(spotifyPageDto.getPageSize(), jamendoPageDto.getPageSize()),
                spotifyPageDto.isHasMore() || jamendoPageDto.isHasMore(),
                spotifyPageDto.getTotalCount() + jamendoPageDto.getTotalCount(),
                mergedTracks,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    private MusicSearchPageDto emptyTrackPage(@NonNull SearchQuery query) {
        return new MusicSearchPageDto(
                query.getFilter().getPage(),
                query.getFilter().getPageSize(),
                false,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    private SearchResultPage mapTrackPage(@NonNull SearchQuery query,
                                          @NonNull MusicSearchPageDto pageDto) {
        return new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                pageDto.isHasMore(),
                pageDto.getTotalCount(),
                rankTracks(pageDto.getTracks(), query.getKeyword()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    private SearchResultPage mapAlbumPage(@NonNull SearchQuery query,
                                          @NonNull MusicSearchPageDto pageDto) {
        return new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                pageDto.isHasMore(),
                pageDto.getTotalCount(),
                Collections.emptyList(),
                rankAlbums(pageDto.getAlbums(), query.getKeyword()),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    private SearchResultPage mapArtistPage(@NonNull SearchQuery query,
                                           @NonNull MusicSearchPageDto pageDto) {
        return new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                pageDto.isHasMore(),
                pageDto.getTotalCount(),
                Collections.emptyList(),
                Collections.emptyList(),
                rankArtists(pageDto.getArtists(), query.getKeyword()),
                Collections.emptyList());
    }

    @NonNull
    private SearchResultPage mapPlaylistPage(@NonNull SearchQuery query,
                                             @NonNull MusicSearchPageDto pageDto) {
        return new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                pageDto.isHasMore(),
                pageDto.getTotalCount(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                rankPlaylists(pageDto.getPlaylists(), query.getKeyword()));
    }

    @NonNull
    private List<SearchTrack> rankTracks(@NonNull List<MusicSearchTrackDto> trackDtos,
                                         @NonNull String keyword) {
        return searchResultRanker.rankTracks(
                searchResultDeduplicator.deduplicateTracks(mapTracks(trackDtos)),
                keyword);
    }

    @NonNull
    private List<SearchAlbum> rankAlbums(@NonNull List<MusicSearchAlbumDto> albumDtos,
                                         @NonNull String keyword) {
        return searchResultRanker.rankAlbums(
                searchResultDeduplicator.deduplicateAlbums(mapAlbums(albumDtos)),
                keyword);
    }

    @NonNull
    private List<SearchArtist> rankArtists(@NonNull List<MusicSearchArtistDto> artistDtos,
                                           @NonNull String keyword) {
        return searchResultRanker.rankArtists(
                searchResultDeduplicator.deduplicateArtists(mapArtists(artistDtos)),
                keyword);
    }

    @NonNull
    private List<com.example.core_domain.search.SearchPlaylist> rankPlaylists(
            @NonNull List<MusicSearchPlaylistDto> playlistDtos,
            @NonNull String keyword) {
        return searchResultRanker.rankPlaylists(
                searchResultDeduplicator.deduplicatePlaylists(mapPlaylists(playlistDtos)),
                keyword);
    }

    @NonNull
    private List<SearchTrack> mapTracks(@NonNull List<MusicSearchTrackDto> trackDtos) {
        List<SearchTrack> tracks = new ArrayList<>();
        for (MusicSearchTrackDto trackDto : trackDtos) {
            tracks.add(new SearchTrack(
                    trackDto.getTrackId(),
                    trackDto.getProviderId(),
                    trackDto.getTitle(),
                    buildTrackSubtitle(trackDto),
                    trackDto.getArtistNames(),
                    trackDto.getAlbumName(),
                    trackDto.getDurationMs(),
                    trackDto.getCoverUrl(),
                    mapAvailability(trackDto.getAvailability()),
                    trackDto.getQualitySummary(),
                    new TrackPlaybackIntent(
                            trackDto.getTrackId(),
                            trackDto.getProviderId(),
                            trackDto.getAlbumId(),
                            trackDto.getPreferredQuality(),
                            trackDto.isRequiresResolve(),
                            trackDto.getCandidateToken(),
                            trackDto.getStreamUrl()),
                    trackDto.isPreviewPlayback(),
                    trackDto.getPlaybackNotice()));
        }
        return tracks;
    }

    @NonNull
    private List<SearchAlbum> mapAlbums(@NonNull List<MusicSearchAlbumDto> albumDtos) {
        List<SearchAlbum> albums = new ArrayList<>();
        for (MusicSearchAlbumDto albumDto : albumDtos) {
            albums.add(new SearchAlbum(
                    albumDto.getAlbumId(),
                    albumDto.getProviderId(),
                    albumDto.getTitle(),
                    albumDto.getArtistNames(),
                    albumDto.getCoverUrl(),
                    albumDto.getTrackCount()));
        }
        return albums;
    }

    @NonNull
    private List<SearchArtist> mapArtists(@NonNull List<MusicSearchArtistDto> artistDtos) {
        List<SearchArtist> artists = new ArrayList<>();
        for (MusicSearchArtistDto artistDto : artistDtos) {
            artists.add(new SearchArtist(
                    artistDto.getArtistId(),
                    artistDto.getProviderId(),
                    artistDto.getName(),
                    artistDto.getCoverUrl()));
        }
        return artists;
    }

    @NonNull
    private List<com.example.core_domain.search.SearchPlaylist> mapPlaylists(
            @NonNull List<MusicSearchPlaylistDto> playlistDtos) {
        List<com.example.core_domain.search.SearchPlaylist> playlists = new ArrayList<>();
        for (MusicSearchPlaylistDto playlistDto : playlistDtos) {
            playlists.add(new com.example.core_domain.search.SearchPlaylist(
                    playlistDto.getPlaylistId(),
                    playlistDto.getProviderId(),
                    playlistDto.getTitle(),
                    playlistDto.getCreatorName(),
                    playlistDto.getCoverUrl(),
                    playlistDto.getTrackCount()));
        }
        return playlists;
    }

    @NonNull
    private String buildTrackSubtitle(@NonNull MusicSearchTrackDto trackDto) {
        if (!TextUtils.isEmpty(trackDto.getSubtitle())) {
            return trackDto.getSubtitle();
        }
        List<String> parts = new ArrayList<>();
        if (!trackDto.getArtistNames().isEmpty()) {
            parts.add(TextUtils.join(" / ", trackDto.getArtistNames()));
        }
        if (!TextUtils.isEmpty(trackDto.getAlbumName())) {
            parts.add(trackDto.getAlbumName());
        }
        return parts.isEmpty() ? trackDto.getProviderId() : TextUtils.join(" · ", parts);
    }

    @NonNull
    private SearchAvailability mapAvailability(@NonNull String availability) {
        if ("AVAILABLE".equalsIgnoreCase(availability)) {
            return SearchAvailability.AVAILABLE;
        }
        if ("RESTRICTED".equalsIgnoreCase(availability)) {
            return SearchAvailability.RESTRICTED;
        }
        if ("UNAVAILABLE".equalsIgnoreCase(availability)) {
            return SearchAvailability.UNAVAILABLE;
        }
        return SearchAvailability.REQUIRES_RESOLVE;
    }
}
