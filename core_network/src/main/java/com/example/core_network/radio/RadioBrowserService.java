package com.example.core_network.radio;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_network.http.NetworkClient;
import com.example.core_network.http.RequestPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RadioBrowserService {

    private static final String USER_AGENT = "FlexMusic/1.0";
    private static final RequestPolicy SEARCH_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(5_000)
            .readTimeoutMs(8_000)
            .retryCount(1)
            .build();
    private static final RequestPolicy CLICK_POLICY = new RequestPolicy.Builder()
            .connectTimeoutMs(4_000)
            .readTimeoutMs(4_000)
            .retryCount(0)
            .build();

    private final NetworkClient networkClient;
    private final RadioBrowserEndpointResolver endpointResolver;

    public RadioBrowserService() {
        this(NetworkClient.getInstance(), new RadioBrowserEndpointResolver());
    }

    public RadioBrowserService(@NonNull NetworkClient networkClient,
                               @NonNull RadioBrowserEndpointResolver endpointResolver) {
        this.networkClient = networkClient;
        this.endpointResolver = endpointResolver;
    }

    @NonNull
    public List<RadioBrowserStation> searchStations(@Nullable String name,
                                                    @Nullable String country,
                                                    int limit) throws IOException {
        Uri.Builder uriBuilder = Uri.parse("/json/stations/search").buildUpon();
        if (!TextUtils.isEmpty(name)) {
            uriBuilder.appendQueryParameter("name", name);
        }
        if (!TextUtils.isEmpty(country)) {
            uriBuilder.appendQueryParameter("country", country);
        }
        uriBuilder.appendQueryParameter("hidebroken", "true");
        uriBuilder.appendQueryParameter("order", "votes");
        uriBuilder.appendQueryParameter("reverse", "true");
        uriBuilder.appendQueryParameter("limit", String.valueOf(Math.max(limit, 1)));
        String path = uriBuilder.build().toString();

        IOException lastException = null;
        for (String endpoint : endpointResolver.getSearchEndpoints()) {
            try {
                return parseStations(networkClient.get(endpoint + path, buildHeaders(), SEARCH_POLICY));
            } catch (IOException e) {
                lastException = e;
            }
        }
        throw lastException != null ? lastException : new IOException("No radio search endpoint available");
    }

    public void registerClick(@NonNull String stationUuid) throws IOException {
        IOException lastException = null;
        for (String endpoint : endpointResolver.getPlaybackEndpoints()) {
            try {
                networkClient.get(endpoint + "/json/url/" + stationUuid, buildHeaders(), CLICK_POLICY);
                return;
            } catch (IOException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }

    @NonNull
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json");
        return headers;
    }

    @NonNull
    private List<RadioBrowserStation> parseStations(@NonNull String body) throws IOException {
        try {
            JSONArray array = new JSONArray(body);
            List<RadioBrowserStation> stations = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String streamUrl = item.optString("url_resolved");
                if (TextUtils.isEmpty(streamUrl)) {
                    streamUrl = item.optString("url");
                }
                if (TextUtils.isEmpty(streamUrl) || TextUtils.isEmpty(item.optString("stationuuid"))) {
                    continue;
                }
                stations.add(new RadioBrowserStation(
                        item.optString("stationuuid"),
                        item.optString("name"),
                        streamUrl,
                        item.optString("homepage"),
                        item.optString("favicon"),
                        item.optString("tags"),
                        item.optString("country"),
                        item.optString("state"),
                        item.optString("language"),
                        item.optInt("votes"),
                        item.optInt("clickcount"),
                        item.optInt("bitrate")));
            }
            return stations;
        } catch (JSONException e) {
            throw new IOException("Failed to parse radio search response", e);
        }
    }
}
