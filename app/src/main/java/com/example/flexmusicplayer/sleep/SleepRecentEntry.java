package com.example.flexmusicplayer.sleep;

import androidx.annotation.NonNull;

public class SleepRecentEntry {

    public enum Type {
        AMBIENCE,
        RADIO
    }

    private final Type type;
    private final String referenceId;
    private final String title;
    private final String subtitle;
    private final long playedAt;

    public SleepRecentEntry(@NonNull Type type,
                            @NonNull String referenceId,
                            @NonNull String title,
                            @NonNull String subtitle,
                            long playedAt) {
        this.type = type;
        this.referenceId = referenceId;
        this.title = title;
        this.subtitle = subtitle;
        this.playedAt = playedAt;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getReferenceId() {
        return referenceId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getSubtitle() {
        return subtitle;
    }

    public long getPlayedAt() {
        return playedAt;
    }
}
