package com.example.core_data.search;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.search.ISearchRepository;
import com.example.core_domain.search.SearchAvailability;
import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchTrack;
import com.example.core_domain.search.TrackPlaybackIntent;
import com.example.core_network.search.MusicSearchService;
import com.example.core_network.search.dto.MusicSearchPageDto;
import com.example.core_network.search.dto.MusicSearchTrackDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OnlineSearchRepository implements ISearchRepository {

    private static final String TAG = "OnlineSearchRepo";

    private final MusicSearchService musicSearchService;
    private final SearchCacheStore searchCacheStore;
    private final SearchResultDeduplicator searchResultDeduplicator;
    private final SearchResultRanker searchResultRanker;

    public OnlineSearchRepository() {
        this(new MusicSearchService(), new SearchCacheStore(),
                new SearchResultDeduplicator(), new SearchResultRanker());
    }

    public OnlineSearchRepository(@NonNull MusicSearchService musicSearchService,
                                  @NonNull SearchCacheStore searchCacheStore,
                                  @NonNull SearchResultDeduplicator searchResultDeduplicator,
                                  @NonNull SearchResultRanker searchResultRanker) {
        this.musicSearchService = musicSearchService;
        this.searchCacheStore = searchCacheStore;
        this.searchResultDeduplicator = searchResultDeduplicator;
        this.searchResultRanker = searchResultRanker;
    }

    @NonNull
    @Override
    public SearchResultPage search(@NonNull SearchQuery query) throws IOException {
        SearchResultPage cachedPage = searchCacheStore.get(query);
        if (cachedPage != null) {
            Log.d(TAG, "search cache hit keyword=" + query.getKeyword()
                    + " page=" + query.getFilter().getPage());
            return cachedPage;
        }

        MusicSearchPageDto pageDto = musicSearchService.searchTracks(
                query.getKeyword(),
                query.getFilter().getPage(),
                query.getFilter().getPageSize());
        List<SearchTrack> tracks = searchResultRanker.rankTracks(
                searchResultDeduplicator.deduplicateTracks(mapTracks(pageDto.getTracks())),
                query.getKeyword());
        SearchResultPage resultPage = new SearchResultPage(
                query.getKeyword(),
                query.getFilter(),
                pageDto.isHasMore(),
                pageDto.getTotalCount(),
                tracks,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
        searchCacheStore.put(query, resultPage);
        Log.d(TAG, "search keyword=" + query.getKeyword()
                + " trackCount=" + tracks.size()
                + " totalCount=" + resultPage.getTotalCount());
        return resultPage;
    }

    @NonNull
    private List<SearchTrack> mapTracks(@NonNull List<MusicSearchTrackDto> trackDtos) {
        List<SearchTrack> tracks = new ArrayList<>();
        for (MusicSearchTrackDto trackDto : trackDtos) {
            tracks.add(new SearchTrack(
                    trackDto.getTrackId(),
                    trackDto.getProviderId(),
                    trackDto.getTitle(),
                    buildSubtitle(trackDto),
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
                            trackDto.getCandidateToken())));
        }
        return tracks;
    }

    @NonNull
    private String buildSubtitle(@NonNull MusicSearchTrackDto trackDto) {
        if (!TextUtils.isEmpty(trackDto.getSubtitle())) {
            return trackDto.getSubtitle();
        }
        return TextUtils.join(" / ", trackDto.getArtistNames()) + " · " + trackDto.getAlbumName();
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
