package com.example.flexmusicplayer.sleep;

import android.text.TextUtils;

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
                "cnr-zgzs",
                "中国之声",
                "央广新闻综合频道 · 官方 HLS",
                "Meditation",
                "http://ngcdn001.cnr.cn/live/zgzs/index.m3u8",
                true));
        stations.add(new SleepRadioStation(
                "cnr-jjzs",
                "经济之声",
                "央广财经资讯频道 · 官方 HLS",
                "Light Music",
                "http://ngcdn002.cnr.cn/live/jjzs/index.m3u8",
                true));
        stations.add(new SleepRadioStation(
                "cnr-dwqzs",
                "大湾区之声",
                "央广粤港澳大湾区频道 · 官方 HLS",
                "ASMR",
                "http://ngcdn007.cnr.cn/live/hxzs/index.m3u8",
                true));
        stations.add(new SleepRadioStation(
                "shanxi-music",
                "山西音乐广播",
                "轻音乐样本频道 · Qingting 64k",
                "Light Music",
                "https://lhttp.qingting.fm/live/4932/64k.mp3",
                false));
        stations.add(new SleepRadioStation(
                "beijing-news",
                "北京新闻广播",
                "城市资讯样本频道 · Qingting 64k",
                "Meditation",
                "https://lhttp.qingting.fm/live/339/64k.mp3",
                false));
        STATIONS = Collections.unmodifiableList(stations);
    }

    private SleepRadioCatalog() {
    }

    @NonNull
    public static List<SleepRadioStation> search(@Nullable String query, @Nullable String category) {
        String safeQuery = normalize(query);
        String safeCategory = normalize(category);
        List<SleepRadioStation> results = new ArrayList<>();
        for (SleepRadioStation station : STATIONS) {
            boolean matchesQuery = TextUtils.isEmpty(safeQuery)
                    || normalize(station.getName()).contains(safeQuery)
                    || normalize(station.getSubtitle()).contains(safeQuery)
                    || normalize(station.getCategory()).contains(safeQuery);
            boolean matchesCategory = TextUtils.isEmpty(safeCategory)
                    || normalize(station.getCategory()).equals(safeCategory);
            if (matchesQuery && matchesCategory) {
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
