package com.example.core_network.search;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;
import com.example.core_network.search.dto.MusicSearchPageDto;
import com.example.core_network.search.dto.MusicSearchTrackDto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicSearchService {

    private static final String TAG = "MusicSearchService";
    private static final String PROVIDER_ID = "jamendo";
    private static final String USER_AGENT = "FlexMusic/1.0";
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
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int offset = (safePage - 1) * safePageSize;
        String clientId = JamendoApiConfig.requireClientId();
        String requestUrl = Uri.parse(endpointResolver.getCatalogEndpoint())
                .buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("limit", String.valueOf(safePageSize))
                .appendQueryParameter("offset", String.valueOf(offset))
                .appendQueryParameter("fullcount", "true")
                .appendQueryParameter("order", "relevance")
                .appendQueryParameter("audioformat", "mp32")
                .appendQueryParameter("imagesize", "300")
                .appendQueryParameter("search", keyword.trim())
                .build()
                .toString();
        Log.d(TAG, "searchTracks backend=" + endpointResolver.getCatalogEndpoint()
                + " keyword=" + keyword.trim()
                + " page=" + safePage
                + " pageSize=" + safePageSize
                + " offset=" + offset);
        String responseBody = networkClient.get(requestUrl, buildHeaders(), SEARCH_POLICY);
        return parseSearchPage(responseBody, safePage, safePageSize, offset);
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
    private MusicSearchPageDto parseSearchPage(@NonNull String responseBody,
                                               int page,
                                               int pageSize,
                                               int offset) throws IOException {
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            JSONObject headers = rootObject.optJSONObject("headers");
            JSONArray results = rootObject.optJSONArray("results");
            if (results == null) {
                results = new JSONArray();
            }
            List<MusicSearchTrackDto> tracks = new ArrayList<>();
            for (int index = 0; index < results.length(); index++) {
                tracks.add(mapTrack(results.getJSONObject(index)));
            }
            int totalCount = headers == null
                    ? tracks.size() + offset
                    : Math.max(headers.optInt("results_fullcount"), tracks.size() + offset);
            boolean hasMore = offset + tracks.size() < totalCount;
            return new MusicSearchPageDto(page, pageSize, hasMore, totalCount, tracks);
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse Jamendo search response", jsonException);
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
        return parts.isEmpty() ? "Jamendo" : android.text.TextUtils.join(" · ", parts);
    }

    @NonNull
    private String mapAudioFormat(@NonNull String preferredQuality) {
        if ("flac".equalsIgnoreCase(preferredQuality)) {
            return "flac";
        }
        if ("ogg".equalsIgnoreCase(preferredQuality)) {
            return "ogg";
        }
        if ("mp31".equalsIgnoreCase(preferredQuality)) {
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
}
