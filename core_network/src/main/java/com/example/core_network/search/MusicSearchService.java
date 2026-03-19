package com.example.core_network.search;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;
import com.example.core_network.search.dto.MusicSearchAlbumDto;
import com.example.core_network.search.dto.MusicSearchArtistDto;
import com.example.core_network.search.dto.MusicSearchPageDto;
import com.example.core_network.search.dto.MusicSearchPlaylistDto;
import com.example.core_network.search.dto.MusicSearchTrackDto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MusicSearchService {

    private static final String TAG = "MusicSearchService";
    private static final String PROVIDER_ID = "jamendo";
    private static final String USER_AGENT = "FlexMusic/1.0";
    private static final int DEFAULT_TREND_LIMIT = 8;
    private static final RequestPolicy SEARCH_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(5_000)
            .readTimeoutMs(8_000)
            .retryCount(1)
            .build();

    private final NetworkClient networkClient;
    private final SearchEndpointResolver endpointResolver;

    public MusicSearchService() {
        this(NetworkClient.getInstance(), new SearchEndpointResolver());
    }

    public MusicSearchService(@NonNull NetworkClient networkClient,
                              @NonNull SearchEndpointResolver endpointResolver) {
        this.networkClient = networkClient;
        this.endpointResolver = endpointResolver;
    }

    @NonNull
    public MusicSearchPageDto searchTracks(@NonNull String keyword,
                                           int page,
                                           int pageSize) throws IOException {
        ParsedPage parsedPage = requestPage(
                endpointResolver.getTracksEndpoint(),
                page,
                pageSize,
                "search",
                keyword,
                "relevance",
                Collections.singletonMap("audioformat", "mp32"));
        List<MusicSearchTrackDto> tracks = new ArrayList<>();
        for (int index = 0; index < parsedPage.results.length(); index++) {
            try {
                tracks.add(mapTrack(parsedPage.results.getJSONObject(index)));
            } catch (JSONException jsonException) {
                throw new IOException("Failed to parse Jamendo track item", jsonException);
            }
        }
        return new MusicSearchPageDto(
                parsedPage.page,
                parsedPage.pageSize,
                parsedPage.hasMore,
                parsedPage.totalCount,
                tracks,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    public MusicSearchPageDto searchAlbums(@NonNull String keyword,
                                           int page,
                                           int pageSize) throws IOException {
        ParsedPage parsedPage = requestPage(
                endpointResolver.getAlbumsEndpoint(),
                page,
                pageSize,
                "namesearch",
                keyword,
                null,
                Collections.singletonMap("imagesize", "300"));
        List<MusicSearchAlbumDto> albums = new ArrayList<>();
        for (int index = 0; index < parsedPage.results.length(); index++) {
            try {
                albums.add(mapAlbum(parsedPage.results.getJSONObject(index)));
            } catch (JSONException jsonException) {
                throw new IOException("Failed to parse Jamendo album item", jsonException);
            }
        }
        return new MusicSearchPageDto(
                parsedPage.page,
                parsedPage.pageSize,
                parsedPage.hasMore,
                parsedPage.totalCount,
                Collections.emptyList(),
                albums,
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    public MusicSearchPageDto searchArtists(@NonNull String keyword,
                                            int page,
                                            int pageSize) throws IOException {
        Map<String, String> extraParams = new LinkedHashMap<>();
        extraParams.put("imagesize", "300");
        extraParams.put("hasimage", "true");
        ParsedPage parsedPage = requestPage(
                endpointResolver.getArtistsEndpoint(),
                page,
                pageSize,
                "namesearch",
                keyword,
                null,
                extraParams);
        List<MusicSearchArtistDto> artists = new ArrayList<>();
        for (int index = 0; index < parsedPage.results.length(); index++) {
            try {
                artists.add(mapArtist(parsedPage.results.getJSONObject(index)));
            } catch (JSONException jsonException) {
                throw new IOException("Failed to parse Jamendo artist item", jsonException);
            }
        }
        return new MusicSearchPageDto(
                parsedPage.page,
                parsedPage.pageSize,
                parsedPage.hasMore,
                parsedPage.totalCount,
                Collections.emptyList(),
                Collections.emptyList(),
                artists,
                Collections.emptyList());
    }

    @NonNull
    public MusicSearchPageDto searchPlaylists(@NonNull String keyword,
                                              int page,
                                              int pageSize) throws IOException {
        ParsedPage parsedPage = requestPage(
                endpointResolver.getPlaylistsEndpoint(),
                page,
                pageSize,
                "namesearch",
                keyword,
                null,
                Collections.singletonMap("imagesize", "300"));
        List<MusicSearchPlaylistDto> playlists = new ArrayList<>();
        for (int index = 0; index < parsedPage.results.length(); index++) {
            try {
                playlists.add(mapPlaylist(parsedPage.results.getJSONObject(index)));
            } catch (JSONException jsonException) {
                throw new IOException("Failed to parse Jamendo playlist item", jsonException);
            }
        }
        return new MusicSearchPageDto(
                parsedPage.page,
                parsedPage.pageSize,
                parsedPage.hasMore,
                parsedPage.totalCount,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                playlists);
    }

    @NonNull
    public List<String> loadAutocompleteSuggestions(@NonNull String prefix, int limit) throws IOException {
        String trimmedPrefix = prefix.trim();
        if (trimmedPrefix.length() < 2) {
            return Collections.emptyList();
        }
        String clientId = JamendoApiConfig.requireClientId();
        Uri.Builder builder = Uri.parse(endpointResolver.getAutocompleteEndpoint())
                .buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("limit", String.valueOf(Math.max(1, limit)))
                .appendQueryParameter("prefix", trimmedPrefix)
                .appendQueryParameter("matchcount", "true");
        builder.appendQueryParameter("entity[]", "tracks");
        builder.appendQueryParameter("entity[]", "albums");
        builder.appendQueryParameter("entity[]", "artists");
        String requestUrl = builder.build().toString();
        Log.d(TAG, "loadAutocompleteSuggestions prefix=" + trimmedPrefix);
        String responseBody = networkClient.get(requestUrl, buildHeaders(), SEARCH_POLICY);
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            JSONObject results = rootObject.optJSONObject("results");
            if (results == null) {
                return Collections.emptyList();
            }
            Map<String, Integer> matches = new LinkedHashMap<>();
            mergeMatches(matches, results.optJSONArray("tracks"));
            mergeMatches(matches, results.optJSONArray("albums"));
            mergeMatches(matches, results.optJSONArray("artists"));
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(matches.entrySet());
            entries.sort((left, right) -> {
                int byCount = Integer.compare(right.getValue(), left.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return left.getKey().compareToIgnoreCase(right.getKey());
            });
            List<String> suggestions = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : entries) {
                suggestions.add(entry.getKey());
            }
            if (suggestions.size() > Math.max(1, limit)) {
                return new ArrayList<>(suggestions.subList(0, Math.max(1, limit)));
            }
            return suggestions;
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse Jamendo autocomplete response", jsonException);
        }
    }

    @NonNull
    public List<String> loadTrendingKeywords(int limit) throws IOException {
        int safeLimit = Math.max(1, limit <= 0 ? DEFAULT_TREND_LIMIT : limit);
        LinkedHashMap<String, Integer> hotTerms = new LinkedHashMap<>();
        addTrendingTerms(hotTerms, requestPage(
                endpointResolver.getTracksEndpoint(),
                1,
                safeLimit,
                null,
                null,
                "popularity_total",
                Collections.singletonMap("audioformat", "mp32")).results, "name");
        addTrendingTerms(hotTerms, requestPage(
                endpointResolver.getAlbumsEndpoint(),
                1,
                safeLimit,
                null,
                null,
                "popularity_total",
                Collections.singletonMap("imagesize", "300")).results, "name");
        Map<String, String> artistParams = new LinkedHashMap<>();
        artistParams.put("imagesize", "300");
        artistParams.put("hasimage", "true");
        addTrendingTerms(hotTerms, requestPage(
                endpointResolver.getArtistsEndpoint(),
                1,
                safeLimit,
                null,
                null,
                "popularity_total",
                artistParams).results, "name");
        List<String> trending = new ArrayList<>(hotTerms.keySet());
        if (trending.size() > safeLimit) {
            return trending.subList(0, safeLimit);
        }
        return trending;
    }

    @NonNull
    public MusicSearchTrackDto getTrackById(@NonNull String trackId,
                                            @NonNull String preferredQuality) throws IOException {
        String clientId = JamendoApiConfig.requireClientId();
        String requestUrl = Uri.parse(endpointResolver.getPlaybackResolveEndpoint())
                .buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("id", trackId)
                .appendQueryParameter("limit", "1")
                .appendQueryParameter("audioformat", mapAudioFormat(preferredQuality))
                .appendQueryParameter("imagesize", "300")
                .build()
                .toString();
        Log.d(TAG, "getTrackById backend=" + endpointResolver.getPlaybackResolveEndpoint()
                + " trackId=" + trackId
                + " quality=" + preferredQuality);
        String responseBody = networkClient.get(requestUrl, buildHeaders(), SEARCH_POLICY);
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            JSONArray results = rootObject.optJSONArray("results");
            if (results == null || results.length() == 0) {
                throw new IOException("Jamendo track not found for " + trackId);
            }
            return mapTrack(results.getJSONObject(0));
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse Jamendo track detail response", jsonException);
        }
    }

    @NonNull
    private ParsedPage requestPage(@NonNull String endpoint,
                                   int page,
                                   int pageSize,
                                   @Nullable String keywordParamName,
                                   @Nullable String keywordValue,
                                   @Nullable String order,
                                   @NonNull Map<String, String> extraParams) throws IOException {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int offset = (safePage - 1) * safePageSize;
        String clientId = JamendoApiConfig.requireClientId();
        Uri.Builder builder = Uri.parse(endpoint)
                .buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("limit", String.valueOf(safePageSize))
                .appendQueryParameter("offset", String.valueOf(offset))
                .appendQueryParameter("fullcount", "true");
        if (!TextUtils.isEmpty(order)) {
            builder.appendQueryParameter("order", order);
        }
        builder.appendQueryParameter("imagesize", "300");
        if (!TextUtils.isEmpty(keywordParamName) && !TextUtils.isEmpty(keywordValue)) {
            builder.appendQueryParameter(keywordParamName, keywordValue.trim());
        }
        for (Map.Entry<String, String> entry : extraParams.entrySet()) {
            if (TextUtils.isEmpty(entry.getKey()) || TextUtils.isEmpty(entry.getValue())) {
                continue;
            }
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        String requestUrl = builder.build().toString();
        Log.d(TAG, "requestPage endpoint=" + endpoint
                + " keyword=" + (keywordValue == null ? "" : keywordValue.trim())
                + " page=" + safePage
                + " pageSize=" + safePageSize
                + " order=" + (order == null ? "" : order));
        String responseBody = networkClient.get(requestUrl, buildHeaders(), SEARCH_POLICY);
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            JSONObject headers = rootObject.optJSONObject("headers");
            JSONArray results = rootObject.optJSONArray("results");
            if (results == null) {
                results = new JSONArray();
            }
            int totalCount = headers == null
                    ? results.length() + offset
                    : Math.max(headers.optInt("results_fullcount"), results.length() + offset);
            boolean hasMore = offset + results.length() < totalCount;
            return new ParsedPage(safePage, safePageSize, totalCount, hasMore, results);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse Jamendo search response", jsonException);
        }
    }

    private void mergeMatches(@NonNull Map<String, Integer> matches, @Nullable JSONArray array)
            throws JSONException {
        if (array == null) {
            return;
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String match = item.optString("match").trim();
            if (match.isEmpty()) {
                continue;
            }
            int count = Math.max(1, item.optInt("count", 1));
            Integer existingCount = matches.get(match);
            if (existingCount == null || count > existingCount) {
                matches.put(match, count);
            }
        }
    }

    private void addTrendingTerms(@NonNull LinkedHashMap<String, Integer> hotTerms,
                                  @NonNull JSONArray results,
                                  @NonNull String fieldName) {
        for (int index = 0; index < results.length(); index++) {
            JSONObject item = results.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String value = item.optString(fieldName).trim();
            if (value.isEmpty()) {
                continue;
            }
            hotTerms.put(value, hotTerms.size());
        }
    }

    @NonNull
    private MusicSearchTrackDto mapTrack(@NonNull JSONObject item) {
        String trackId = item.optString("id");
        String albumId = item.optString("album_id");
        String title = item.optString("name");
        String artistName = item.optString("artist_name");
        String albumName = item.optString("album_name");
        String audioUrl = item.optString("audio");
        String licenseUrl = item.optString("license_ccurl");
        String qualitySummary = buildQualitySummary(audioUrl, licenseUrl);
        return new MusicSearchTrackDto(
                trackId,
                PROVIDER_ID,
                albumId,
                title,
                buildSubtitle(artistName, albumName),
                Collections.singletonList(artistName),
                albumName,
                item.optLong("duration", 0L) * 1000L,
                item.optString("image", item.optString("album_image")),
                audioUrl.isEmpty() ? "UNAVAILABLE" : "AVAILABLE",
                qualitySummary,
                "mp32",
                true,
                trackId,
                audioUrl);
    }

    @NonNull
    private MusicSearchAlbumDto mapAlbum(@NonNull JSONObject item) {
        String artistName = item.optString("artist_name");
        return new MusicSearchAlbumDto(
                item.optString("id"),
                PROVIDER_ID,
                item.optString("name"),
                Collections.singletonList(artistName),
                item.optString("image"),
                resolveTrackCount(item, "tracks_count", "tracks", "track_count"));
    }

    @NonNull
    private MusicSearchArtistDto mapArtist(@NonNull JSONObject item) {
        return new MusicSearchArtistDto(
                item.optString("id"),
                PROVIDER_ID,
                item.optString("name"),
                item.optString("image"));
    }

    @NonNull
    private MusicSearchPlaylistDto mapPlaylist(@NonNull JSONObject item) {
        return new MusicSearchPlaylistDto(
                item.optString("id"),
                PROVIDER_ID,
                item.optString("name"),
                item.optString("user_name", item.optString("creation_name")),
                item.optString("image"),
                resolveTrackCount(item, "tracks_count", "tracks", "track_count"));
    }

    private int resolveTrackCount(@NonNull JSONObject item, @NonNull String... keys) {
        for (String key : keys) {
            Object value = item.opt(key);
            if (value instanceof Number) {
                return Math.max(0, ((Number) value).intValue());
            }
            if (value instanceof JSONArray) {
                return ((JSONArray) value).length();
            }
            if (value instanceof String) {
                try {
                    return Math.max(0, Integer.parseInt((String) value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    @NonNull
    private String buildSubtitle(@NonNull String artistName, @NonNull String albumName) {
        if (albumName.isEmpty()) {
            return artistName;
        }
        return artistName + " · " + albumName;
    }

    @NonNull
    private String buildQualitySummary(@NonNull String audioUrl, @NonNull String licenseUrl) {
        List<String> parts = new ArrayList<>();
        if (audioUrl.contains("format=mp32")) {
            parts.add("MP3 VBR");
        } else if (audioUrl.contains("format=ogg")) {
            parts.add("OGG");
        } else if (audioUrl.contains("format=flac")) {
            parts.add("FLAC");
        } else if (!audioUrl.isEmpty()) {
            parts.add("MP3");
        }
        if (!licenseUrl.isEmpty()) {
            parts.add("CC");
        }
        return parts.isEmpty() ? "Jamendo" : TextUtils.join(" · ", parts);
    }

    @NonNull
    private String mapAudioFormat(@NonNull String preferredQuality) {
        String normalized = preferredQuality.trim().toLowerCase(Locale.ROOT);
        if ("flac".equals(normalized)) {
            return "flac";
        }
        if ("ogg".equals(normalized)) {
            return "ogg";
        }
        if ("mp31".equals(normalized)) {
            return "mp31";
        }
        return "mp32";
    }

    @NonNull
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json");
        return headers;
    }

    private static final class ParsedPage {
        private final int page;
        private final int pageSize;
        private final int totalCount;
        private final boolean hasMore;
        private final JSONArray results;

        private ParsedPage(int page,
                           int pageSize,
                           int totalCount,
                           boolean hasMore,
                           @NonNull JSONArray results) {
            this.page = page;
            this.pageSize = pageSize;
            this.totalCount = Math.max(0, totalCount);
            this.hasMore = hasMore;
            this.results = results;
        }
    }
}
