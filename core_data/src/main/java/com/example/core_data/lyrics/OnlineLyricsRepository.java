package com.example.core_data.lyrics;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.lyrics.ILyricsRepository;
import com.example.core_domain.lyrics.LyricsLineData;
import com.example.core_domain.lyrics.LyricsQuery;
import com.example.core_domain.lyrics.LyricsResult;
import com.example.core_network.lyrics.LyricsService;
import com.example.core_network.lyrics.dto.RemoteLyricsDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OnlineLyricsRepository implements ILyricsRepository {

    private static final String TAG = "OnlineLyricsRepo";
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final LyricsService lyricsService;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();

    public OnlineLyricsRepository() {
        this(new LyricsService());
    }

    public OnlineLyricsRepository(@NonNull LyricsService lyricsService) {
        this.lyricsService = lyricsService;
    }

    @NonNull
    @Override
    public synchronized LyricsResult loadLyrics(@NonNull LyricsQuery query) throws IOException {
        String cacheKey = buildCacheKey(query);
        CacheEntry cachedEntry = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cachedEntry != null && cachedEntry.expiresAtMs > now) {
            Log.d(TAG, "loadLyrics cache hit key=" + cacheKey);
            return cachedEntry.result;
        }

        LyricsResult result = resolveLyrics(query);
        cache.put(cacheKey, new CacheEntry(result, now + CACHE_TTL_MS));
        Log.d(TAG, "loadLyrics sourceId=" + query.getSourceId()
                + " synced=" + result.isSynced()
                + " lineCount=" + result.getLines().size());
        return result;
    }

    @NonNull
    private LyricsResult resolveLyrics(@NonNull LyricsQuery query) throws IOException {
        RemoteLyricsDto exactMatch = lyricsService.fetchExactLyrics(query);
        if (exactMatch != null) {
            LyricsResult exactResult = toLyricsResult(exactMatch, query);
            if (!exactResult.getLines().isEmpty()) {
                return exactResult;
            }
        }

        List<RemoteLyricsDto> searchMatches = lyricsService.searchLyrics(query);
        RemoteLyricsDto bestMatch = selectBestMatch(searchMatches, query);
        if (bestMatch == null) {
            return LyricsResult.empty(lyricsService.getProviderId());
        }
        return toLyricsResult(bestMatch, query);
    }

    @Nullable
    private RemoteLyricsDto selectBestMatch(@NonNull List<RemoteLyricsDto> matches,
                                            @NonNull LyricsQuery query) {
        RemoteLyricsDto bestMatch = null;
        int bestScore = Integer.MIN_VALUE;
        for (RemoteLyricsDto match : matches) {
            int score = score(match, query);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = match;
            }
        }
        return bestMatch;
    }

    private int score(@NonNull RemoteLyricsDto dto, @NonNull LyricsQuery query) {
        int score = 0;
        String normalizedTitle = normalize(query.getTitle());
        String normalizedArtist = normalize(query.getArtistName());
        String normalizedAlbum = normalize(query.getAlbumName());
        String candidateTitle = normalize(dto.getTrackName());
        String candidateArtist = normalize(dto.getArtistName());
        String candidateAlbum = normalize(dto.getAlbumName());
        if (candidateTitle.equals(normalizedTitle)) {
            score += 120;
        } else if (candidateTitle.contains(normalizedTitle) || normalizedTitle.contains(candidateTitle)) {
            score += 80;
        }
        if (!normalizedArtist.isEmpty()) {
            if (candidateArtist.equals(normalizedArtist)) {
                score += 60;
            } else if (candidateArtist.contains(normalizedArtist) || normalizedArtist.contains(candidateArtist)) {
                score += 30;
            }
        }
        if (!normalizedAlbum.isEmpty()) {
            if (candidateAlbum.equals(normalizedAlbum)) {
                score += 24;
            } else if (candidateAlbum.contains(normalizedAlbum) || normalizedAlbum.contains(candidateAlbum)) {
                score += 10;
            }
        }
        long queryDurationMs = query.getDurationMs();
        long remoteDurationMs = dto.getDurationMs();
        if (queryDurationMs > 0L && remoteDurationMs > 0L) {
            long deltaMs = Math.abs(queryDurationMs - remoteDurationMs);
            if (deltaMs <= 1_500L) {
                score += 20;
            } else if (deltaMs <= 4_000L) {
                score += 10;
            }
        }
        if (!TextUtils.isEmpty(dto.getSyncedLyrics())) {
            score += 15;
        } else if (!TextUtils.isEmpty(dto.getPlainLyrics())) {
            score += 6;
        }
        if (dto.isInstrumental()) {
            score -= 20;
        }
        return score;
    }

    @NonNull
    private LyricsResult toLyricsResult(@NonNull RemoteLyricsDto dto, @NonNull LyricsQuery query) {
        List<LyricsLineData> syncedLines = parseSyncedLyrics(dto.getSyncedLyrics());
        if (!syncedLines.isEmpty()) {
            return new LyricsResult(true, lyricsService.getProviderId(), syncedLines);
        }
        List<LyricsLineData> plainLines = parsePlainLyrics(dto.getPlainLyrics(), query.getDurationMs());
        if (!plainLines.isEmpty()) {
            return new LyricsResult(false, lyricsService.getProviderId(), plainLines);
        }
        return LyricsResult.empty(lyricsService.getProviderId());
    }

    @NonNull
    private List<LyricsLineData> parseSyncedLyrics(@Nullable String syncedLyrics) {
        if (TextUtils.isEmpty(syncedLyrics)) {
            return Collections.emptyList();
        }
        List<LyricsLineData> lines = new ArrayList<>();
        String[] rows = syncedLyrics.split("\\r?\\n");
        for (String row : rows) {
            if (TextUtils.isEmpty(row)) {
                continue;
            }
            int closingBracketIndex = row.indexOf(']');
            if (!row.startsWith("[") || closingBracketIndex <= 1) {
                continue;
            }
            long timestampMs = parseTimestamp(row.substring(1, closingBracketIndex));
            String text = row.substring(closingBracketIndex + 1).trim();
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            lines.add(new LyricsLineData(timestampMs, text));
        }
        return lines;
    }

    @NonNull
    private List<LyricsLineData> parsePlainLyrics(@Nullable String plainLyrics, long durationMs) {
        if (TextUtils.isEmpty(plainLyrics)) {
            return Collections.emptyList();
        }
        String[] rows = plainLyrics.split("\\r?\\n");
        List<String> texts = new ArrayList<>();
        for (String row : rows) {
            String trimmed = row.trim();
            if (!trimmed.isEmpty()) {
                texts.add(trimmed);
            }
        }
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }
        List<LyricsLineData> lines = new ArrayList<>();
        long safeDurationMs = Math.max(durationMs, texts.size() * 4_000L);
        long intervalMs = Math.max(2_500L, safeDurationMs / Math.max(texts.size(), 1));
        for (int index = 0; index < texts.size(); index++) {
            lines.add(new LyricsLineData(index * intervalMs, texts.get(index)));
        }
        return lines;
    }

    private long parseTimestamp(@NonNull String rawTimestamp) {
        try {
            String[] minuteSecondParts = rawTimestamp.split(":");
            if (minuteSecondParts.length != 2) {
                return 0L;
            }
            long minutes = Long.parseLong(minuteSecondParts[0]);
            String[] secondMillisecondParts = minuteSecondParts[1].split("\\.");
            long seconds = Long.parseLong(secondMillisecondParts[0]);
            long hundredths = secondMillisecondParts.length > 1
                    ? Long.parseLong(secondMillisecondParts[1])
                    : 0L;
            long milliseconds = secondMillisecondParts.length > 1
                    ? normalizeFractionToMs(secondMillisecondParts[1], hundredths)
                    : 0L;
            return minutes * 60_000L + seconds * 1_000L + milliseconds;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long normalizeFractionToMs(@NonNull String fraction, long value) {
        if (fraction.length() == 3) {
            return value;
        }
        if (fraction.length() == 2) {
            return value * 10L;
        }
        if (fraction.length() == 1) {
            return value * 100L;
        }
        return value;
    }

    @NonNull
    private String buildCacheKey(@NonNull LyricsQuery query) {
        return normalize(query.getTitle())
                + "|"
                + normalize(query.getArtistName())
                + "|"
                + normalize(query.getAlbumName())
                + "|"
                + (query.getDurationMs() / 1000L);
    }

    @NonNull
    private String normalize(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final class CacheEntry {
        private final LyricsResult result;
        private final long expiresAtMs;

        private CacheEntry(@NonNull LyricsResult result, long expiresAtMs) {
            this.result = result;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
