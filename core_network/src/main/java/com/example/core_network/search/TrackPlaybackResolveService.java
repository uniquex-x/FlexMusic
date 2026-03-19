package com.example.core_network.search;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_network.search.dto.MusicSearchTrackDto;
import com.example.core_network.search.dto.TrackPlaybackCandidateDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackPlaybackResolveService {

    private static final String TAG = "TrackPlaybackResolve";
    private static final long CANDIDATE_TTL_MS = 5 * 60 * 1000L;

    private final MusicSearchService musicSearchService;

    public TrackPlaybackResolveService() {
        this(new MusicSearchService());
    }

    public TrackPlaybackResolveService(@NonNull MusicSearchService musicSearchService) {
        this.musicSearchService = musicSearchService;
    }

    @NonNull
    public List<TrackPlaybackCandidateDto> resolveCandidates(@NonNull String providerId,
                                                             @NonNull String trackId,
                                                             @NonNull String candidateToken,
                                                             @NonNull String preferredQuality) throws IOException {
        Log.d(TAG, "resolveCandidates backend=Jamendo"
                + " providerId=" + providerId
                + " trackId=" + trackId
                + " token=" + candidateToken
                + " quality=" + preferredQuality);
        MusicSearchTrackDto trackDto = musicSearchService.getTrackById(trackId, preferredQuality);
        if (trackDto.getStreamUrl().isEmpty()) {
            throw new IOException("No playback candidate for " + trackId);
        }

        long expiresAtMs = System.currentTimeMillis() + CANDIDATE_TTL_MS;
        List<TrackPlaybackCandidateDto> candidates = new ArrayList<>();
        candidates.add(new TrackPlaybackCandidateDto(
                providerId + ":" + trackId + ":primary",
                trackDto.getStreamUrl(),
                Collections.emptyMap(),
                trackDto.getQualitySummary(),
                expiresAtMs,
                false,
                100));
        return candidates;
    }
}
