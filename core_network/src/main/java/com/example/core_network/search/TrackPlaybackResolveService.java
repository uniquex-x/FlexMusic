package com.example.core_network.search;

import android.text.TextUtils;
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
                                                             @NonNull String preferredQuality,
                                                             @NonNull String directStreamUrl) throws IOException {
        if (hasUsableDirectStreamUrl(directStreamUrl)) {
            Log.d(TAG, "resolveCandidates backend=Direct"
                    + " providerId=" + providerId
                    + " trackId=" + trackId
                    + " quality=" + preferredQuality);
            return buildDirectCandidates(providerId, trackId, preferredQuality, directStreamUrl);
        }
        Log.d(TAG, "resolveCandidates backend=Jamendo"
                + " providerId=" + providerId
                + " trackId=" + trackId
                + " token=" + candidateToken
                + " quality=" + preferredQuality);
        MusicSearchTrackDto trackDto = musicSearchService.getTrackById(trackId, preferredQuality);
        if (trackDto.getStreamUrl().isEmpty()) {
            throw new IOException("No playback candidate for " + trackId);
        }

        return buildDirectCandidates(
                providerId,
                trackId,
                trackDto.getQualitySummary(),
                trackDto.getStreamUrl());
    }

    @NonNull
    private List<TrackPlaybackCandidateDto> buildDirectCandidates(@NonNull String providerId,
                                                                  @NonNull String trackId,
                                                                  @NonNull String qualityLabel,
                                                                  @NonNull String directStreamUrl) {
        long expiresAtMs = System.currentTimeMillis() + CANDIDATE_TTL_MS;
        List<TrackPlaybackCandidateDto> candidates = new ArrayList<>();
        candidates.add(new TrackPlaybackCandidateDto(
                providerId + ":" + trackId + ":primary",
                directStreamUrl,
                Collections.emptyMap(),
                qualityLabel,
                expiresAtMs,
                false,
                100));
        return candidates;
    }

    private boolean hasUsableDirectStreamUrl(@NonNull String directStreamUrl) {
        String trimmedUrl = directStreamUrl.trim();
        return !TextUtils.isEmpty(trimmedUrl) && !"null".equalsIgnoreCase(trimmedUrl);
    }
}
