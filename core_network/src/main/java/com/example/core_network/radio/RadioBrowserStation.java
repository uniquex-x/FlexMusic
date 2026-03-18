package com.example.core_network.radio;

import androidx.annotation.NonNull;

public class RadioBrowserStation {

    private final String stationUuid;
    private final String name;
    private final String streamUrl;
    private final String homepage;
    private final String favicon;
    private final String tags;
    private final String country;
    private final String state;
    private final String language;
    private final int votes;
    private final int clickCount;
    private final int bitrate;

    public RadioBrowserStation(@NonNull String stationUuid,
                               @NonNull String name,
                               @NonNull String streamUrl,
                               @NonNull String homepage,
                               @NonNull String favicon,
                               @NonNull String tags,
                               @NonNull String country,
                               @NonNull String state,
                               @NonNull String language,
                               int votes,
                               int clickCount,
                               int bitrate) {
        this.stationUuid = stationUuid;
        this.name = name;
        this.streamUrl = streamUrl;
        this.homepage = homepage;
        this.favicon = favicon;
        this.tags = tags;
        this.country = country;
        this.state = state;
        this.language = language;
        this.votes = votes;
        this.clickCount = clickCount;
        this.bitrate = bitrate;
    }

    @NonNull
    public String getStationUuid() {
        return stationUuid;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getStreamUrl() {
        return streamUrl;
    }

    @NonNull
    public String getHomepage() {
        return homepage;
    }

    @NonNull
    public String getFavicon() {
        return favicon;
    }

    @NonNull
    public String getTags() {
        return tags;
    }

    @NonNull
    public String getCountry() {
        return country;
    }

    @NonNull
    public String getState() {
        return state;
    }

    @NonNull
    public String getLanguage() {
        return language;
    }

    public int getVotes() {
        return votes;
    }

    public int getClickCount() {
        return clickCount;
    }

    public int getBitrate() {
        return bitrate;
    }
}
