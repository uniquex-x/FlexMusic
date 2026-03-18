package com.example.core_network.stream;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.player.PlaybackSourceResolver;
import com.example.core_domain.player.ResolvedPlayableSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class NetworkPlaybackSourceResolver implements PlaybackSourceResolver {

    private static final long CACHE_TTL_MS = 2 * 60 * 1000L;

    private final AudioStreamProbeApi audioStreamProbeApi;
    private final Map<String, CachedResolvedSource> resolvedSourceCache = new LinkedHashMap<>();

    public NetworkPlaybackSourceResolver() {
        this(new AudioStreamProbeApi());
    }

    public NetworkPlaybackSourceResolver(@NonNull AudioStreamProbeApi audioStreamProbeApi) {
        this.audioStreamProbeApi = audioStreamProbeApi;
    }

    @NonNull
    @Override
    public ResolvedPlayableSource resolve(@NonNull PlaybackRequest request) throws IOException {
        String originalUrl = request.getOriginalUrl().trim();
        if (originalUrl.isEmpty()) {
            throw new IOException("Playback source is empty for " + request.getSourceId());
        }

        if (AudioStreamProbeApi.isLocalUri(originalUrl) || !AudioStreamProbeApi.isNetworkUri(originalUrl)) {
            return new ResolvedPlayableSource(
                    request.getSourceId(),
                    originalUrl,
                    originalUrl,
                    "",
                    "",
                    request.isLiveStream(),
                    true,
                    !request.isLiveStream(),
                    0L);
        }

        ResolvedPlayableSource cachedSource = getCachedSource(originalUrl);
        if (cachedSource != null) {
            return cachedSource;
        }

        try {
            AudioStreamProbeResult result = audioStreamProbeApi.probe(originalUrl);
            boolean liveStream = resolveLiveStreamFlag(request, result);
            ResolvedPlayableSource resolvedSource = new ResolvedPlayableSource(
                    request.getSourceId(),
                    originalUrl,
                    result.getResolvedUrl(),
                    result.getContentType(),
                    AudioStreamProbeApi.DEFAULT_USER_AGENT,
                    liveStream,
                    false,
                    !liveStream,
                    result.getProbeLatencyMs());
            cacheResolvedSource(originalUrl, resolvedSource);
            return resolvedSource;
        } catch (IOException probeError) {
            return new ResolvedPlayableSource(
                    request.getSourceId(),
                    originalUrl,
                    originalUrl,
                    "",
                    AudioStreamProbeApi.DEFAULT_USER_AGENT,
                    request.isLiveStream(),
                    false,
                    !request.isLiveStream(),
                    -1L);
        }
    }

    private boolean resolveLiveStreamFlag(@NonNull PlaybackRequest request,
                                          @NonNull AudioStreamProbeResult result) {
        if (request.isLiveStream()) {
            return true;
        }
        String contentType = result.getContentType().toLowerCase(Locale.ROOT);
        String resolvedUrl = result.getResolvedUrl().toLowerCase(Locale.ROOT);
        if (contentType.contains("mpegurl")
                || contentType.contains("vnd.apple.mpegurl")
                || contentType.contains("audio/aacp")
                || contentType.contains("application/vnd.apple.mpegurl")) {
            return true;
        }
        return !TextUtils.isEmpty(resolvedUrl)
                && (resolvedUrl.contains(".m3u8")
                || resolvedUrl.contains("/live")
                || resolvedUrl.contains("stream"));
    }

    private synchronized void cacheResolvedSource(@NonNull String originalUrl,
                                                  @NonNull ResolvedPlayableSource resolvedSource) {
        resolvedSourceCache.put(originalUrl, new CachedResolvedSource(
                resolvedSource,
                System.currentTimeMillis() + CACHE_TTL_MS));
    }

    private synchronized ResolvedPlayableSource getCachedSource(@NonNull String originalUrl) {
        CachedResolvedSource cached = resolvedSourceCache.get(originalUrl);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtMs < System.currentTimeMillis()) {
            resolvedSourceCache.remove(originalUrl);
            return null;
        }
        return cached.resolvedSource;
    }

    private static final class CachedResolvedSource {
        private final ResolvedPlayableSource resolvedSource;
        private final long expiresAtMs;

        private CachedResolvedSource(@NonNull ResolvedPlayableSource resolvedSource,
                                     long expiresAtMs) {
            this.resolvedSource = resolvedSource;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
