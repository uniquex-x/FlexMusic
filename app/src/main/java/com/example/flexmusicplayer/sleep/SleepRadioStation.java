package com.example.flexmusicplayer.sleep;

import androidx.annotation.NonNull;

public class SleepRadioStation {

    private final String id;
    private final String name;
    private final String subtitle;
    private final String category;
    private final String streamUrl;
    private final boolean official;

    public SleepRadioStation(@NonNull String id,
                             @NonNull String name,
                             @NonNull String subtitle,
                             @NonNull String category,
                             @NonNull String streamUrl,
                             boolean official) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.category = category;
        this.streamUrl = streamUrl;
        this.official = official;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getSubtitle() {
        return subtitle;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    @NonNull
    public String getStreamUrl() {
        return streamUrl;
    }

    public boolean isOfficial() {
        return official;
    }
}
