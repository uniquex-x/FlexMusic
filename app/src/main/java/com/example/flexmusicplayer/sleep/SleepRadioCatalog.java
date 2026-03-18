package com.example.flexmusicplayer.sleep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SleepRadioCatalog {

    private static final List<SleepRadioStation> STATIONS;

    static {
        List<SleepRadioStation> stations = new ArrayList<>();
        stations.add(new SleepRadioStation(
                "cnr-zgzs-1061",
                "中国之声",
                "北京 / 全国新闻综合",
                "FM 106.1",
                "http://ngcdn001.cnr.cn/live/zgzs/index.m3u8",
                true));
        stations.add(new SleepRadioStation(
                "cnr-jjzs-966",
                "经济之声",
                "北京 / 财经资讯",
                "FM 96.6",
                "http://ngcdn002.cnr.cn/live/jjzs/index.m3u8",
                true));
        stations.add(new SleepRadioStation(
                "cnr-dwqzs-1012",
                "大湾区之声",
                "粤港澳大湾区 / 综合资讯",
                "FM 101.2",
                "http://ngcdn007.cnr.cn/live/hxzs/index.m3u8",
                true));
        stations.add(new SleepRadioStation(
                "beijing-music-974",
                "北京音乐广播",
                "北京 / 流行音乐",
                "FM 97.4",
                "https://lhttp.qingting.fm/live/336/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "shanghai-news-990",
                "上海第一财经广播",
                "上海 / 财经资讯",
                "FM 97.7",
                "https://lhttp.qingting.fm/live/274/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "shanxi-music-897",
                "山西音乐广播",
                "太原 / 音乐陪伴",
                "FM 89.7",
                "https://lhttp.qingting.fm/live/4932/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "beijing-news-1006",
                "北京新闻广播",
                "北京 / 城市资讯",
                "FM 100.6",
                "https://lhttp.qingting.fm/live/339/64k.mp3",
                false));
        STATIONS = Collections.unmodifiableList(stations);
    }

    private SleepRadioCatalog() {
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
