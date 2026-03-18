package com.example.core_network.radio;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_network.http.SimpleHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RadioBrowserService {

    private static final List<String> MIRRORS = Collections.unmodifiableList(Arrays.asList(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
    ));
    private static final String USER_AGENT = "FlexMusic/1.0";

    private final SimpleHttpClient httpClient;

    public RadioBrowserService(@NonNull SimpleHttpClient httpClient) {
        this.httpClient = httpClient;
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
        for (String mirror : MIRRORS) {
            try {
                return parseStations(httpClient.get(mirror + path, buildHeaders()));
            } catch (IOException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return Collections.emptyList();
    }

    public void registerClick(@NonNull String stationUuid) throws IOException {
        IOException lastException = null;
        for (String mirror : MIRRORS) {
            try {
                httpClient.get(mirror + "/json/url/" + stationUuid, buildHeaders());
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
