package com.example.core_data.search;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.search.ITrackPlaybackResolver;
import com.example.core_domain.search.TrackPlaybackCandidate;
import com.example.core_domain.search.TrackPlaybackIntent;
import com.example.core_network.search.TrackPlaybackResolveService;
import com.example.core_network.search.dto.TrackPlaybackCandidateDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrackPlaybackRepository implements ITrackPlaybackResolver {

    private static final String TAG = "TrackPlaybackRepo";

    private final TrackPlaybackResolveService trackPlaybackResolveService;
    private final Map<String, CachedCandidates> candidateCache = new LinkedHashMap<>();

    public TrackPlaybackRepository() {
        this(new TrackPlaybackResolveService());
    }

    public TrackPlaybackRepository(@NonNull TrackPlaybackResolveService trackPlaybackResolveService) {
        this.trackPlaybackResolveService = trackPlaybackResolveService;
    }

    @NonNull
    @Override
    public synchronized List<TrackPlaybackCandidate> resolveCandidates(@NonNull TrackPlaybackIntent playbackIntent) throws IOException {
        String cacheKey = buildCacheKey(playbackIntent);
        CachedCandidates cachedCandidates = candidateCache.get(cacheKey);
        if (cachedCandidates != null && cachedCandidates.expiresAtMs > System.currentTimeMillis()) {
            Log.d(TAG, "candidate cache hit trackId=" + playbackIntent.getTrackId());
            return cachedCandidates.candidates;
        }

        List<TrackPlaybackCandidateDto> candidateDtos = trackPlaybackResolveService.resolveCandidates(
                playbackIntent.getProviderId(),
                playbackIntent.getTrackId(),
                playbackIntent.getCandidateToken(),
                playbackIntent.getPreferredQuality());
        List<TrackPlaybackCandidate> candidates = mapCandidates(candidateDtos);
        Collections.sort(candidates, Comparator.comparingInt(TrackPlaybackCandidate::getConfidence).reversed());
        long expiresAtMs = resolveCacheExpiry(candidates);
        candidateCache.put(cacheKey, new CachedCandidates(candidates, expiresAtMs));
        Log.d(TAG, "resolveCandidates trackId=" + playbackIntent.getTrackId()
                + " candidateCount=" + candidates.size()
                + " expiresAtMs=" + expiresAtMs);
        return candidates;
    }

    @NonNull
    private List<TrackPlaybackCandidate> mapCandidates(@NonNull List<TrackPlaybackCandidateDto> candidateDtos) {
        List<TrackPlaybackCandidate> candidates = new ArrayList<>();
        for (TrackPlaybackCandidateDto candidateDto : candidateDtos) {
            candidates.add(new TrackPlaybackCandidate(
                    candidateDto.getSourceId(),
                    candidateDto.getOriginalUrl(),
                    candidateDto.getHeaders(),
                    candidateDto.getQualityLabel(),
                    candidateDto.getExpiresAtMs(),
                    candidateDto.isLive(),
                    candidateDto.getConfidence()));
        }
        return candidates;
    }

    private long resolveCacheExpiry(@NonNull List<TrackPlaybackCandidate> candidates) {
        long minExpiry = Long.MAX_VALUE;
        for (TrackPlaybackCandidate candidate : candidates) {
            if (candidate.getExpiresAtMs() <= 0L) {
                continue;
            }
            minExpiry = Math.min(minExpiry, candidate.getExpiresAtMs());
        }
        return minExpiry == Long.MAX_VALUE ? System.currentTimeMillis() : minExpiry;
    }

    @NonNull
    private String buildCacheKey(@NonNull TrackPlaybackIntent playbackIntent) {
        return playbackIntent.getProviderId()
                + "|"
                + playbackIntent.getTrackId()
                + "|"
                + playbackIntent.getCandidateToken()
                + "|"
                + playbackIntent.getPreferredQuality();
    }

    private static final class CachedCandidates {
        private final List<TrackPlaybackCandidate> candidates;
        private final long expiresAtMs;

        private CachedCandidates(@NonNull List<TrackPlaybackCandidate> candidates, long expiresAtMs) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.expiresAtMs = expiresAtMs;
        }
    }
}
