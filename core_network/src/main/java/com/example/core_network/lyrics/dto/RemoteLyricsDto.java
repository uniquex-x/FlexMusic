package com.example.core_network.lyrics.dto;

import androidx.annotation.NonNull;

public final class RemoteLyricsDto {

    private final long remoteId;
    private final String trackName;
    private final String artistName;
    private final String albumName;
    private final long durationMs;
    private final boolean instrumental;
    private final String plainLyrics;
    private final String syncedLyrics;

    public RemoteLyricsDto(long remoteId,
                           @NonNull String trackName,
                           @NonNull String artistName,
                           @NonNull String albumName,
                           long durationMs,
                           boolean instrumental,
                           @NonNull String plainLyrics,
                           @NonNull String syncedLyrics) {
        this.remoteId = remoteId;
        this.trackName = trackName;
        this.artistName = artistName;
        this.albumName = albumName;
        this.durationMs = Math.max(0L, durationMs);
        this.instrumental = instrumental;
        this.plainLyrics = plainLyrics;
        this.syncedLyrics = syncedLyrics;
    }

    public long getRemoteId() {
        return remoteId;
    }

    @NonNull
    public String getTrackName() {
        return trackName;
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

    public boolean isInstrumental() {
        return instrumental;
    }

    @NonNull
    public String getPlainLyrics() {
        return plainLyrics;
    }

    @NonNull
    public String getSyncedLyrics() {
        return syncedLyrics;
    }
}
