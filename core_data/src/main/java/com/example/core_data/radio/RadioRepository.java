package com.example.core_data.radio;

import android.util.Log;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.radio.RadioStation;
import com.example.core_network.radio.RadioBrowserService;
import com.example.core_network.radio.RadioBrowserStation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RadioRepository {

    private static final String TAG = "RadioRepository";
    private static final int SEARCH_LIMIT = 24;
    private static final Pattern FREQUENCY_PATTERN = Pattern.compile("(\\d{2,3}(?:\\.\\d)?)");
    private static final Pattern RADIO_BROWSER_UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final RadioBrowserService radioBrowserService;
    private final Object stationCacheLock = new Object();
    private final Map<String, RadioStation> stationCache = new LinkedHashMap<>();

    public RadioRepository() {
        this(new RadioBrowserService());
    }

    public RadioRepository(@NonNull RadioBrowserService radioBrowserService) {
        this.radioBrowserService = radioBrowserService;
    }

    @NonNull
    public List<RadioStation> search(@NonNull String query) throws IOException {
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            List<RadioStation> stations = mapStations(radioBrowserService.searchStations(null, "China", SEARCH_LIMIT), "");
            rememberStations(stations);
            return stations;
        }

        Map<String, RadioStation> merged = new LinkedHashMap<>();
        for (String candidate : buildCandidateQueries(trimmedQuery)) {
            List<RadioBrowserStation> chinaMatches = radioBrowserService.searchStations(candidate, "China", SEARCH_LIMIT);
            appendMatches(merged, mapStations(chinaMatches, trimmedQuery));
            if (merged.size() >= SEARCH_LIMIT) {
                break;
            }
            List<RadioBrowserStation> globalMatches = radioBrowserService.searchStations(candidate, null, SEARCH_LIMIT);
            appendMatches(merged, mapStations(globalMatches, trimmedQuery));
            if (merged.size() >= SEARCH_LIMIT) {
                break;
            }
        }

        List<RadioStation> results = new ArrayList<>(merged.values());
        Collections.sort(results, new Comparator<RadioStation>() {
            @Override
            public int compare(RadioStation a, RadioStation b) {
                return Integer.compare(scoreStation(b, trimmedQuery), scoreStation(a, trimmedQuery));
            }
        });
        if (results.size() > SEARCH_LIMIT) {
            results = new ArrayList<>(results.subList(0, SEARCH_LIMIT));
        }
        rememberStations(results);
        return results;
    }

    public void registerClick(@NonNull RadioStation station) {
        if (!looksLikeRadioBrowserStationId(station.getId())) {
            Log.d(TAG, "skip registerClick non-radio-browser stationId=" + station.getId());
            return;
        }
        try {
            radioBrowserService.registerClick(station.getId());
        } catch (IOException ignored) {
            Log.w(TAG, "registerClick failed stationId=" + station.getId(), ignored);
        }
    }

    @Nullable
    public RadioStation findById(@Nullable String stationId) {
        if (stationId == null) {
            return null;
        }
        synchronized (stationCacheLock) {
            return stationCache.get(stationId);
        }
    }

    private void rememberStations(@NonNull List<RadioStation> stations) {
        synchronized (stationCacheLock) {
            for (RadioStation station : stations) {
                stationCache.put(station.getId(), station);
            }
        }
    }

    private boolean looksLikeRadioBrowserStationId(@Nullable String stationId) {
        return stationId != null && RADIO_BROWSER_UUID_PATTERN.matcher(stationId).matches();
    }

    private void appendMatches(@NonNull Map<String, RadioStation> merged,
                               @NonNull List<RadioStation> stations) {
        for (RadioStation station : stations) {
            merged.put(station.getId(), station);
        }
    }

    @NonNull
    private List<String> buildCandidateQueries(@NonNull String query) {
        List<String> candidates = new ArrayList<>();
        candidates.add(query);
        String normalized = query.replace("FM", "").replace("fm", "").trim();
        if (!normalized.equals(query) && !normalized.isEmpty()) {
            candidates.add(normalized);
        }
        Matcher matcher = FREQUENCY_PATTERN.matcher(query);
        if (matcher.find()) {
            String frequency = matcher.group(1);
            if (!candidates.contains(frequency)) {
                candidates.add(frequency);
            }
            String fmFrequency = "FM " + frequency;
            if (!candidates.contains(fmFrequency)) {
                candidates.add(fmFrequency);
            }
        }
        return candidates;
    }

    @NonNull
    private List<RadioStation> mapStations(@NonNull List<RadioBrowserStation> source,
                                           @NonNull String query) {
        List<RadioStation> stations = new ArrayList<>();
        for (RadioBrowserStation item : source) {
            String frequency = resolveFrequency(item, query);
            String subtitle = buildSubtitle(item);
            stations.add(new RadioStation(
                    item.getStationUuid(),
                    cleanName(item.getName()),
                    subtitle,
                    frequency,
                    item.getStreamUrl(),
                    isOfficial(item)));
        }
        return stations;
    }

    @NonNull
    private String resolveFrequency(@NonNull RadioBrowserStation station, @NonNull String query) {
        String candidate = extractFrequency(station.getName());
        if (candidate == null) {
            candidate = extractFrequency(station.getTags());
        }
        if (candidate == null) {
            candidate = extractFrequency(query);
        }
        return candidate == null ? "LIVE" : "FM " + candidate;
    }

    @Nullable
    private String extractFrequency(@NonNull String value) {
        Matcher matcher = FREQUENCY_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    @NonNull
    private String buildSubtitle(@NonNull RadioBrowserStation station) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(station.getCountry())) {
            parts.add(station.getCountry());
        }
        if (!TextUtils.isEmpty(station.getState())) {
            parts.add(station.getState());
        }
        if (!TextUtils.isEmpty(station.getLanguage())) {
            parts.add(station.getLanguage());
        }
        if (!TextUtils.isEmpty(station.getTags())) {
            parts.add(station.getTags().replace(",", " / "));
        }
        if (station.getBitrate() > 0) {
            parts.add(station.getBitrate() + "kbps");
        }
        if (parts.isEmpty()) {
            return "Online Radio";
        }
        return TextUtils.join(" · ", parts);
    }

    @NonNull
    private String cleanName(@NonNull String name) {
        String cleaned = name.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? "Unknown Radio" : cleaned;
    }

    private boolean isOfficial(@NonNull RadioBrowserStation station) {
        String homepage = station.getHomepage().toLowerCase(Locale.ROOT);
        return homepage.contains("radio.cn")
                || homepage.contains("cnr.cn")
                || homepage.contains(".gov.cn");
    }

    private int scoreStation(@NonNull RadioStation station, @NonNull String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedName = station.getName().toLowerCase(Locale.ROOT);
        String normalizedSubtitle = station.getSubtitle().toLowerCase(Locale.ROOT);
        String normalizedFrequency = station.getFrequency().toLowerCase(Locale.ROOT);
        int score = 0;
        if (normalizedName.contains(normalizedQuery)) {
            score += 30;
        }
        if (normalizedFrequency.contains(normalizedQuery)) {
            score += 40;
        }
        if (normalizedSubtitle.contains("china")) {
            score += 10;
        }
        if (normalizedSubtitle.contains(normalizedQuery)) {
            score += 10;
        }
        if (station.isOfficial()) {
            score += 8;
        }
        return score;
    }
}
