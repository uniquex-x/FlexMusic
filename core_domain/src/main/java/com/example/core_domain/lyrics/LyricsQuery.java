package com.example.core_domain.lyrics;

import androidx.annotation.NonNull;

public final class LyricsQuery {

    private final String sourceId;
    private final String title;
    private final String artistName;
    private final String albumName;
    private final long durationMs;

    public LyricsQuery(@NonNull String sourceId,
                       @NonNull String title,
                       @NonNull String artistName,
                       @NonNull String albumName,
                       long durationMs) {
        this.sourceId = sourceId;
        this.title = title.trim();
        this.artistName = artistName.trim();
        this.albumName = albumName.trim();
        this.durationMs = Math.max(0L, durationMs);
    }

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getArtistName() {
        return artistName;
    }

    @NonNull
    public String getAlbumName() {
        return albumName;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
