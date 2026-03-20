package com.example.flexmusicplayer.sleep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SleepRadioCatalog {

    private static final int FEATURED_LIMIT = 10;
    private static final List<SleepRadioStation> STATIONS;

    static {
        List<SleepRadioStation> stations = new ArrayList<>();
        stations.add(new SleepRadioStation(
                "beijing-music-974",
                "北京音乐广播",
                "北京 / 流行音乐",
                "FM 97.4",
                "https://lhttp.qingting.fm/live/332/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "beijing-traffic-1039",
                "北京交通广播",
                "北京 / 路况出行",
                "FM 103.9",
                "https://lhttp.qingting.fm/live/336/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "beijing-news-945",
                "北京新闻广播",
                "北京 / 城市资讯",
                "FM 94.5",
                "https://lhttp.qingting.fm/live/339/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "beijing-arts-876",
                "北京文艺广播",
                "北京 / 文艺人文",
                "FM 87.6",
                "https://lhttp.qingting.fm/live/333/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "shanghai-news-990",
                "上海新闻广播",
                "上海 / 新闻资讯",
                "FM 99.0",
                "https://lhttp.qingting.fm/live/270/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "shanghai-love-radio-1037",
                "上海流行音乐 Love Radio",
                "上海 / 慢节奏流行",
                "FM 103.7",
                "https://lhttp.qingting.fm/live/273/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "shanghai-top-101-1017",
                "上海动感101",
                "上海 / 热门流行",
                "FM 101.7",
                "https://lhttp.qingting.fm/live/274/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "shanghai-finance-909",
                "第一财经广播",
                "上海 / 财经资讯",
                "FM 90.9",
                "https://lhttp.qingting.fm/live/276/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "guangdong-music-993",
                "广东音乐之声",
                "广东 / 音乐陪伴",
                "FM 99.3",
                "https://lhttp.qingting.fm/live/1260/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "guangdong-traffic-1052",
                "广东交通之声",
                "广东 / 交通资讯",
                "FM 105.2",
                "https://lhttp.qingting.fm/live/1262/64k.mp3",
                false));
        STATIONS = Collections.unmodifiableList(stations);
    }

    private SleepRadioCatalog() {
    }

    @NonNull
    public static List<SleepRadioStation> featuredStations() {
        return new ArrayList<>(STATIONS.subList(0, Math.min(FEATURED_LIMIT, STATIONS.size())));
    }

    @NonNull
    public static List<SleepRadioStation> search(@Nullable String query) {
        String safeQuery = normalize(query);
        List<SleepRadioStation> results = new ArrayList<>();
        for (SleepRadioStation station : STATIONS) {
            boolean matchesQuery = safeQuery.isEmpty()
                    || normalize(station.getName()).contains(safeQuery)
                    || normalize(station.getSubtitle()).contains(safeQuery)
                    || normalize(station.getFrequency()).contains(safeQuery);
            if (matchesQuery) {
                results.add(station);
            }
        }
        return results;
    }

    @Nullable
    public static SleepRadioStation findById(@Nullable String stationId) {
        if (stationId == null) {
            return null;
        }
        for (SleepRadioStation station : STATIONS) {
            if (station.getId().equals(stationId)) {
                return station;
            }
        }
        return null;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
