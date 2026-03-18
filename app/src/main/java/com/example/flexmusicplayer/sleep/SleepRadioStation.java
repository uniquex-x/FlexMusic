package com.example.flexmusicplayer.sleep;

import androidx.annotation.NonNull;

import com.example.flexmusicplayer.model.Song;

public class SleepRadioStation {

    private final String id;
    private final String name;
    private final String subtitle;
    private final String frequency;
    private final String streamUrl;
    private final boolean official;

    public SleepRadioStation(@NonNull String id,
                             @NonNull String name,
                             @NonNull String subtitle,
                             @NonNull String frequency,
                             @NonNull String streamUrl,
                             boolean official) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.frequency = frequency;
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
    public String getFrequency() {
        return frequency;
    }

    @NonNull
    public String getStreamUrl() {
        return streamUrl;
    }

    public boolean isOfficial() {
        return official;
    }

    @NonNull
    public Song toSong() {
        Song song = new Song(id.hashCode(), name, frequency + " · " + subtitle, "FM Radio", 0, streamUrl);
        song.setRadioStream(true);
        song.setSourceId(id);
        return song;
    }
}
