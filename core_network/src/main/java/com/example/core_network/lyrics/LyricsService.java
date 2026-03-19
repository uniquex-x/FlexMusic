package com.example.core_network.lyrics;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.lyrics.LyricsQuery;
import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;
import com.example.core_network.lyrics.dto.RemoteLyricsDto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LyricsService {

    private static final String TAG = "LyricsService";
    private static final String PROVIDER_ID = "lrclib";
    private static final String BASE_URL = "https://lrclib.net/api";
    private static final String USER_AGENT = "FlexMusic/1.0";
    private static final RequestPolicy LYRICS_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(4_000)
            .readTimeoutMs(6_000)
            .retryCount(1)
            .build();

    private final NetworkClient networkClient;

    public LyricsService() {
        this(NetworkClient.getInstance());
    }

    public LyricsService(@NonNull NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @Nullable
    public RemoteLyricsDto fetchExactLyrics(@NonNull LyricsQuery query) throws IOException {
        Uri.Builder builder = Uri.parse(BASE_URL + "/get").buildUpon()
                .appendQueryParameter("track_name", query.getTitle());
        if (!TextUtils.isEmpty(query.getArtistName())) {
            builder.appendQueryParameter("artist_name", query.getArtistName());
        }
        if (!TextUtils.isEmpty(query.getAlbumName())) {
            builder.appendQueryParameter("album_name", query.getAlbumName());
        }
        if (query.getDurationMs() > 0L) {
            builder.appendQueryParameter("duration", String.valueOf(query.getDurationMs() / 1000L));
        }
        String requestUrl = builder.build().toString();
        Log.d(TAG, "fetchExactLyrics title=" + query.getTitle()
                + " artist=" + query.getArtistName()
                + " album=" + query.getAlbumName());
        try {
            String responseBody = networkClient.get(requestUrl, buildHeaders(), LYRICS_POLICY);
            return parseLyricsObject(new JSONObject(responseBody));
        } catch (IOException ioException) {
            if (ioException.getMessage() != null && ioException.getMessage().contains("HTTP 404")) {
                return null;
            }
            throw ioException;
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse exact lyrics payload", jsonException);
        }
    }

    @NonNull
    public List<RemoteLyricsDto> searchLyrics(@NonNull LyricsQuery query) throws IOException {
        String requestUrl = Uri.parse(BASE_URL + "/search")
                .buildUpon()
                .appendQueryParameter("q", buildSearchQuery(query))
                .build()
                .toString();
        Log.d(TAG, "searchLyrics q=" + buildSearchQuery(query));
        String responseBody = networkClient.get(requestUrl, buildHeaders(), LYRICS_POLICY);
        try {
            JSONArray array = new JSONArray(responseBody);
            List<RemoteLyricsDto> matches = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                matches.add(parseLyricsObject(item));
            }
            return matches;
        } catch (JSONException jsonException) {
            throw new IOException("Failed to parse lyrics search payload", jsonException);
        }
    }

    @NonNull
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @NonNull
    private String buildSearchQuery(@NonNull LyricsQuery query) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(query.getArtistName())) {
            parts.add(query.getArtistName());
        }
        if (!TextUtils.isEmpty(query.getTitle())) {
            parts.add(query.getTitle());
        }
        if (!TextUtils.isEmpty(query.getAlbumName())) {
            parts.add(query.getAlbumName());
        }
        return TextUtils.join(" ", parts);
    }

    @NonNull
    private RemoteLyricsDto parseLyricsObject(@NonNull JSONObject object) {
        return new RemoteLyricsDto(
                object.optLong("id", 0L),
                object.optString("trackName"),
                object.optString("artistName"),
                object.optString("albumName"),
                Math.round(object.optDouble("duration", 0d) * 1000d),
                object.optBoolean("instrumental", false),
                object.optString("plainLyrics"),
                object.optString("syncedLyrics"));
    }

    @NonNull
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json");
        return headers;
    }
}
