package com.example.core_network.stream;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.player.IPlaybackWarmupEngine;
import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.player.PlaybackSourceResolver;
import com.example.core_domain.player.PlaybackWarmupRequest;
import com.example.core_domain.player.PlaybackWarmupSnapshot;
import com.example.core_domain.player.ResolvedPlayableSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class NetworkPlaybackSourceResolver implements PlaybackSourceResolver, IPlaybackWarmupEngine {

    private static final String TAG = "PlaybackSourceResolver";
    private static final long CACHE_TTL_MS = 2 * 60 * 1000L;

    private final NetworkWarmupEngine networkWarmupEngine;
    private final Map<String, CachedResolvedSource> resolvedSourceCache = new LinkedHashMap<>();
    private final Set<String> retainedSourceIds = new LinkedHashSet<>();

    public NetworkPlaybackSourceResolver() {
        this(new AudioStreamProbeApi(), new HostWarmupClient());
    }

    public NetworkPlaybackSourceResolver(@NonNull AudioStreamProbeApi audioStreamProbeApi) {
        this(audioStreamProbeApi, new HostWarmupClient());
    }

    NetworkPlaybackSourceResolver(@NonNull AudioStreamProbeApi audioStreamProbeApi,
                                  @NonNull HostWarmupClient hostWarmupClient) {
        this.networkWarmupEngine = new NetworkWarmupEngine(hostWarmupClient, audioStreamProbeApi);
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

        CachedResolvedSource cachedResolvedSource = getCachedEntry(request.getSourceId(), originalUrl);
        if (cachedResolvedSource != null) {
            if (cachedResolvedSource.warmupSnapshot != null) {
                SeekablePlaybackProxyServer.getInstance().promotePreparedSession(request.getSourceId());
                cacheResolvedSource(
                        request.getSourceId(),
                        originalUrl,
                        cachedResolvedSource.resolvedSource,
                        null);
                Log.d(TAG, "warmup hit sourceId=" + request.getSourceId()
                        + " level=" + cachedResolvedSource.warmupSnapshot.snapshot.getCompletedLevel()
                        + " resolvedUrl=" + cachedResolvedSource.resolvedSource.getResolvedUrl());
            } else {
                Log.d(TAG, "resolved cache hit sourceId=" + request.getSourceId()
                        + " resolvedUrl=" + cachedResolvedSource.resolvedSource.getResolvedUrl());
            }
            return cachedResolvedSource.resolvedSource;
        }

        Log.d(TAG, "skip cold-start probe sourceId=" + request.getSourceId()
                + " url=" + originalUrl
                + " live=" + request.isLiveStream());
        String playbackUrl = originalUrl;
        if (NetworkStreamWarmupPolicy.shouldUseSeekableProxy(request, originalUrl)) {
            try {
                SeekablePlaybackProxyServer.ProxySessionHandle proxySessionHandle =
                        SeekablePlaybackProxyServer.getInstance().openSession(
                                request.getSourceId(),
                                originalUrl,
                                AudioStreamProbeApi.DEFAULT_USER_AGENT);
                playbackUrl = proxySessionHandle.getLocalUrl();
                Log.d(TAG, "use seekable proxy sourceId=" + request.getSourceId()
                        + " remoteUrl=" + originalUrl
                        + " localUrl=" + playbackUrl);
            } catch (IOException ioException) {
                Log.w(TAG, "seekable proxy open failed sourceId=" + request.getSourceId()
                        + " url=" + originalUrl, ioException);
            }
        }
        ResolvedPlayableSource resolvedSource = new ResolvedPlayableSource(
                request.getSourceId(),
                originalUrl,
                playbackUrl,
                "",
                AudioStreamProbeApi.DEFAULT_USER_AGENT,
                request.isLiveStream(),
                false,
                !request.isLiveStream(),
                0L);
        cacheResolvedSource(request.getSourceId(), originalUrl, resolvedSource, null);
        Log.d(TAG, "warmup miss sourceId=" + request.getSourceId()
                + " fallback=cold_path"
                + " resolvedUrl=" + playbackUrl);
        return resolvedSource;
    }

    @NonNull
    @Override
    public PlaybackWarmupSnapshot warmup(@NonNull PlaybackWarmupRequest request) {
        CachedResolvedSource cachedSource = getCachedEntry(request.getSourceId(), request.getOriginalUrl());
        if (cachedSource != null
                && cachedSource.warmupSnapshot != null
                && cachedSource.warmupSnapshot.snapshot.getCompletedLevel().ordinal() >= request.getTargetLevel().ordinal()) {
            Log.d(TAG, "warmup hit sourceId=" + request.getSourceId()
                    + " level=" + cachedSource.warmupSnapshot.snapshot.getCompletedLevel());
            return cachedSource.warmupSnapshot.snapshot;
        }

        NetworkWarmupEngine.PreparedWarmupResult warmupResult = networkWarmupEngine.warmup(request);
        cacheResolvedSource(
                request.getSourceId(),
                request.getOriginalUrl(),
                warmupResult.getResolvedSource(),
                new CachedWarmupSnapshot(warmupResult.getSnapshot(), warmupResult.getProxySessionHandle()));
        Log.d(TAG, "warmup ready sourceId=" + request.getSourceId()
                + " requested=" + request.getTargetLevel()
                + " completed=" + warmupResult.getSnapshot().getCompletedLevel()
                + " probeLatencyMs=" + warmupResult.getSnapshot().getProbeLatencyMs()
                + " totalLatencyMs=" + warmupResult.getSnapshot().getTotalLatencyMs());
        return warmupResult.getSnapshot();
    }

    @Override
    public synchronized void retainWarmup(@NonNull String sourceId) {
        retainedSourceIds.add(sourceId);
        CachedResolvedSource cachedResolvedSource = resolvedSourceCache.get(sourceId);
        if (cachedResolvedSource == null) {
            Log.d(TAG, "retain warmup pending sourceId=" + sourceId);
            return;
        }
        if (cachedResolvedSource.retainedForSession) {
            return;
        }
        resolvedSourceCache.put(sourceId, new CachedResolvedSource(
                cachedResolvedSource.resolvedSource,
                cachedResolvedSource.originalUrl,
                cachedResolvedSource.warmupSnapshot,
                Long.MAX_VALUE,
                true));
        Log.d(TAG, "retain warmup sourceId=" + sourceId
                + " resolvedUrl=" + cachedResolvedSource.resolvedSource.getResolvedUrl());
    }

    @Override
    public void cancelWarmup(@NonNull String sourceId) {
        CachedResolvedSource cachedResolvedSource;
        synchronized (this) {
            cachedResolvedSource = resolvedSourceCache.get(sourceId);
            if (cachedResolvedSource == null) {
                return;
            }
            if (cachedResolvedSource.retainedForSession) {
                Log.d(TAG, "retain cache keep sourceId=" + sourceId
                        + " resolvedUrl=" + cachedResolvedSource.resolvedSource.getResolvedUrl());
                return;
            }
            resolvedSourceCache.remove(sourceId);
        }
        if (cachedResolvedSource != null && cachedResolvedSource.warmupSnapshot != null) {
            SeekablePlaybackProxyServer.ProxySessionHandle handle = cachedResolvedSource.warmupSnapshot.proxySessionHandle;
            if (handle != null) {
                SeekablePlaybackProxyServer.getInstance().releasePreparedSession(sourceId);
                Log.d(TAG, "prepared session released sourceId=" + sourceId);
            } else {
                Log.d(TAG, "warmup cache released sourceId=" + sourceId
                        + " level=" + cachedResolvedSource.warmupSnapshot.snapshot.getCompletedLevel());
            }
        }
    }

    @Override
    public synchronized void cancelAllWarmups() {
        retainedSourceIds.clear();
        for (String sourceId : new LinkedHashMap<>(resolvedSourceCache).keySet()) {
            cancelWarmup(sourceId);
        }
    }

    private synchronized void cacheResolvedSource(@NonNull String sourceId,
                                                  @NonNull String originalUrl,
                                                  @NonNull ResolvedPlayableSource resolvedSource,
                                                  @Nullable CachedWarmupSnapshot warmupSnapshot) {
        boolean retainedForSession = retainedSourceIds.contains(sourceId);
        resolvedSourceCache.put(sourceId, new CachedResolvedSource(
                resolvedSource,
                originalUrl,
                warmupSnapshot,
                retainedForSession ? Long.MAX_VALUE : System.currentTimeMillis() + CACHE_TTL_MS,
                retainedForSession));
    }

    @Nullable
    private synchronized CachedResolvedSource getCachedEntry(@NonNull String sourceId,
                                                             @NonNull String originalUrl) {
        CachedResolvedSource cached = resolvedSourceCache.get(sourceId);
        if (cached == null) {
            return null;
        }
        if (!cached.originalUrl.equals(originalUrl)) {
            releasePreparedHandleLocked(sourceId, cached);
            resolvedSourceCache.remove(sourceId);
            retainedSourceIds.remove(sourceId);
            return null;
        }
        if (!cached.retainedForSession && cached.expiresAtMs < System.currentTimeMillis()) {
            releasePreparedHandleLocked(sourceId, cached);
            resolvedSourceCache.remove(sourceId);
            return null;
        }
        return cached;
    }

    private void releasePreparedHandleLocked(@NonNull String sourceId,
                                             @NonNull CachedResolvedSource cachedResolvedSource) {
        if (cachedResolvedSource.warmupSnapshot == null
                || cachedResolvedSource.warmupSnapshot.proxySessionHandle == null) {
            return;
        }
        SeekablePlaybackProxyServer.getInstance().releasePreparedSession(sourceId);
    }

    private static final class CachedResolvedSource {
        private final ResolvedPlayableSource resolvedSource;
        private final String originalUrl;
        private final CachedWarmupSnapshot warmupSnapshot;
        private final long expiresAtMs;
        private final boolean retainedForSession;

        private CachedResolvedSource(@NonNull ResolvedPlayableSource resolvedSource,
                                     @NonNull String originalUrl,
                                     @Nullable CachedWarmupSnapshot warmupSnapshot,
                                     long expiresAtMs,
                                     boolean retainedForSession) {
            this.resolvedSource = resolvedSource;
            this.originalUrl = originalUrl;
            this.warmupSnapshot = warmupSnapshot;
            this.expiresAtMs = expiresAtMs;
            this.retainedForSession = retainedForSession;
        }
    }

    private static final class CachedWarmupSnapshot {
        private final PlaybackWarmupSnapshot snapshot;
        private final SeekablePlaybackProxyServer.ProxySessionHandle proxySessionHandle;

        private CachedWarmupSnapshot(@NonNull PlaybackWarmupSnapshot snapshot,
                                     @Nullable SeekablePlaybackProxyServer.ProxySessionHandle proxySessionHandle) {
            this.snapshot = snapshot;
            this.proxySessionHandle = proxySessionHandle;
        }
    }
}
