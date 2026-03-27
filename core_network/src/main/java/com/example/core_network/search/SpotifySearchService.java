package com.example.core_network.search;

import android.net.Uri;
import android.text.TextUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpotifySearchService {

    private static final String TAG = "SpotifySearchService";
    private static final String PROVIDER_ID = "spotify";
    private static final String SEARCH_ENDPOINT = "https://api.spotify.com/v1/search";
    private static final int SEARCH_LIMIT_MAX = 10;
    private static final RequestPolicy SEARCH_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(5_000)
            .readTimeoutMs(8_000)
            .retryCount(1)
            .build();

    private final NetworkClient networkClient;
    private final SpotifyAuthService spotifyAuthService;

    public SpotifySearchService() {
        this(NetworkClient.getInstance(), new SpotifyAuthService());
    }

    public SpotifySearchService(@NonNull NetworkClient networkClient,
                                @NonNull SpotifyAuthService spotifyAuthService) {
        this.networkClient = networkClient;
        this.spotifyAuthService = spotifyAuthService;
    }

    @NonNull
    public MusicSearchPageDto searchTracks(@NonNull String keyword,
                                           int page,
                                           int pageSize) throws IOException {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return emptyPage(page, pageSize);
        }
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, SEARCH_LIMIT_MAX));
        int offset = (safePage - 1) * safePageSize;
        String market = SpotifyApiConfig.resolveMarket();
        Uri requestUri = Uri.parse(SEARCH_ENDPOINT)
                .buildUpon()
                .appendQueryParameter("q", trimmedKeyword)
                .appendQueryParameter("type", "track")
                .appendQueryParameter("market", market)
                .appendQueryParameter("limit", String.valueOf(safePageSize))
                .appendQueryParameter("offset", String.valueOf(offset))
                .build();
        Log.d(TAG, "searchTracks providerId=" + PROVIDER_ID
                + " market=" + market
                + " page=" + safePage
                + " pageSize=" + safePageSize
                + " keyword=" + trimmedKeyword);
        String responseBody = networkClient.get(requestUri.toString(), buildHeaders(), SEARCH_POLICY);
        try {
            JSONObject rootObject = new JSONObject(responseBody);
            JSONObject tracksObject = rootObject.optJSONObject("tracks");
            if (tracksObject == null) {
                return emptyPage(page, pageSize);
            }
            JSONArray items = tracksObject.optJSONArray("items");
            List<MusicSearchTrackDto> tracks = new ArrayList<>();
            if (items != null) {
                for (int index = 0; index < items.length(); index++) {
                    JSONObject item = items.optJSONObject(index);
                    if (item == null) {
                        continue;
                    }
                    MusicSearchTrackDto trackDto = mapTrack(item);
                    if (!trackDto.getStreamUrl().isEmpty()) {
                        tracks.add(trackDto);
                    }
                }
            }
            int totalCount = Math.max(0, tracksObject.optInt("total", tracks.size()));
            boolean hasMore = tracksObject.optString("next").trim().length() > 0
                    || offset + safePageSize < totalCount;
            return new MusicSearchPageDto(
                    safePage,
                    safePageSize,
                    hasMore,
                    totalCount,
                    tracks,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList());
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse Spotify track search response", jsonException);
        }
    }

    @NonNull
    private Map<String, String> buildHeaders() throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + spotifyAuthService.getAccessToken());
        return headers;
    }

    @NonNull
    private MusicSearchPageDto emptyPage(int page, int pageSize) {
        return new MusicSearchPageDto(
                page,
                pageSize,
                false,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    @NonNull
    private MusicSearchTrackDto mapTrack(@NonNull JSONObject item) {
        JSONArray artistItems = item.optJSONArray("artists");
        List<String> artistNames = new ArrayList<>();
        if (artistItems != null) {
            for (int index = 0; index < artistItems.length(); index++) {
                JSONObject artistObject = artistItems.optJSONObject(index);
                if (artistObject == null) {
                    continue;
                }
                String artistName = artistObject.optString("name").trim();
                if (!artistName.isEmpty()) {
                    artistNames.add(artistName);
                }
            }
        }

        JSONObject albumObject = item.optJSONObject("album");
        String albumId = normalizeOptionalString(albumObject == null ? "" : albumObject.optString("id"));
        String albumName = normalizeOptionalString(albumObject == null ? "" : albumObject.optString("name"));
        String coverUrl = resolveCoverUrl(albumObject);
        String previewUrl = normalizeOptionalString(item.optString("preview_url"));
        String subtitle = buildSubtitle(artistNames, albumName);
        return new MusicSearchTrackDto(
                normalizeOptionalString(item.optString("id")),
                PROVIDER_ID,
                albumId,
                normalizeOptionalString(item.optString("name")),
                subtitle,
                artistNames,
                albumName,
                item.optLong("duration_ms", 0L),
                coverUrl,
                previewUrl.isEmpty() ? "UNAVAILABLE" : "RESTRICTED",
                "Spotify 30s Demo",
                "preview",
                true,
                normalizeOptionalString(item.optString("id")),
                previewUrl,
                !previewUrl.isEmpty(),
                previewUrl.isEmpty() ? "" : "无版权，免费试听中");
    }

    @NonNull
    private String resolveCoverUrl(JSONObject albumObject) {
        if (albumObject == null) {
            return "";
        }
        JSONArray images = albumObject.optJSONArray("images");
        if (images == null || images.length() == 0) {
            return "";
        }
        JSONObject imageObject = images.optJSONObject(images.length() - 1);
        if (imageObject == null) {
            imageObject = images.optJSONObject(0);
        }
        return imageObject == null ? "" : normalizeOptionalString(imageObject.optString("url"));
    }

    @NonNull
    private String buildSubtitle(@NonNull List<String> artistNames, @NonNull String albumName) {
        List<String> parts = new ArrayList<>();
        if (!artistNames.isEmpty()) {
            parts.add(TextUtils.join(" / ", artistNames));
        }
        if (!albumName.isEmpty()) {
            parts.add(albumName);
        }
        return parts.isEmpty() ? "Spotify" : TextUtils.join(" · ", parts);
    }

    @NonNull
    private String normalizeOptionalString(String value) {
        if (value == null) {
            return "";
        }
        String trimmedValue = value.trim();
        if ("null".equalsIgnoreCase(trimmedValue)) {
            return "";
        }
        return trimmedValue;
    }
}
