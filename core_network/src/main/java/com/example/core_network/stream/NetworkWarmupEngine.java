package com.example.core_network.stream;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.player.PlaybackWarmupLevel;
import com.example.core_domain.player.PlaybackWarmupRequest;
import com.example.core_domain.player.PlaybackWarmupSnapshot;
import com.example.core_domain.player.ResolvedPlayableSource;

import java.io.IOException;

final class NetworkWarmupEngine {

    private static final String TAG = "NetworkWarmupEngine";
    private static final int HEAD_CACHE_BYTES = 64 * 1024;

    private final HostWarmupClient hostWarmupClient;
    private final AudioStreamProbeApi audioStreamProbeApi;

    NetworkWarmupEngine(@NonNull HostWarmupClient hostWarmupClient,
                        @NonNull AudioStreamProbeApi audioStreamProbeApi) {
        this.hostWarmupClient = hostWarmupClient;
        this.audioStreamProbeApi = audioStreamProbeApi;
    }

    @NonNull
    PreparedWarmupResult warmup(@NonNull PlaybackWarmupRequest request) {
        long startedAtMs = SystemClock.elapsedRealtime();
        String originalUrl = request.getOriginalUrl().trim();
        if (originalUrl.isEmpty()) {
            return new PreparedWarmupResult(
                    buildSnapshot(request, originalUrl, originalUrl, "", "", "",
                            false, request.isLiveStream(), false, false, -1L, 0L,
                            PlaybackWarmupLevel.NONE, startedAtMs),
                    null,
                    buildResolvedSource(request, originalUrl, originalUrl, "", request.isLiveStream(),
                            false, !request.isLiveStream(), 0L));
        }

        if (AudioStreamProbeApi.isLocalUri(originalUrl) || !AudioStreamProbeApi.isNetworkUri(originalUrl)) {
            return new PreparedWarmupResult(
                    buildSnapshot(request, originalUrl, originalUrl, "", "", "",
                            false, request.isLiveStream(), true, !request.isLiveStream(), -1L, 0L,
                            PlaybackWarmupLevel.NONE, startedAtMs),
                    null,
                    buildResolvedSource(request, originalUrl, originalUrl, "", request.isLiveStream(),
                            true, !request.isLiveStream(), 0L));
        }

        Log.d(TAG, "warmup start sourceId=" + request.getSourceId()
                + " targetLevel=" + request.getTargetLevel()
                + " url=" + originalUrl);

        PlaybackWarmupLevel completedLevel = PlaybackWarmupLevel.NONE;
        String resolvedUrl = originalUrl;
        String localPlaybackUrl = "";
        String contentType = "";
        boolean liveStream = request.isLiveStream();
        boolean seekable = !request.isLiveStream();
        boolean acceptRanges = false;
        long contentLength = -1L;
        long probeLatencyMs = 0L;
        SeekablePlaybackProxyServer.ProxySessionHandle proxySessionHandle = null;

        if (request.getTargetLevel().includes(PlaybackWarmupLevel.HOST)) {
            try {
                long hostLatencyMs = hostWarmupClient.warmup(originalUrl);
                completedLevel = PlaybackWarmupLevel.HOST;
                Log.d(TAG, "warmup host ready sourceId=" + request.getSourceId()
                        + " elapsedMs=" + hostLatencyMs
                        + " url=" + originalUrl);
            } catch (IOException hostError) {
                Log.w(TAG, "warmup host failed sourceId=" + request.getSourceId()
                        + " url=" + originalUrl, hostError);
            }
        }

        if (request.getTargetLevel().includes(PlaybackWarmupLevel.URL_METADATA)) {
            try {
                PlaybackRequest playbackRequest = new PlaybackRequest(
                        request.getSourceId(),
                        originalUrl,
                        request.isLiveStream());
                AudioStreamProbeResult probeResult = audioStreamProbeApi.probe(originalUrl);
                resolvedUrl = probeResult.getResolvedUrl();
                contentType = probeResult.getContentType();
                probeLatencyMs = probeResult.getProbeLatencyMs();
                contentLength = probeResult.getContentLength();
                acceptRanges = probeResult.isAcceptRanges();
                liveStream = NetworkStreamWarmupPolicy.resolveLiveStreamFlag(playbackRequest, probeResult);
                seekable = !liveStream;
                completedLevel = PlaybackWarmupLevel.URL_METADATA;
                Log.d(TAG, "warmup metadata ready sourceId=" + request.getSourceId()
                        + " resolvedUrl=" + resolvedUrl
                        + " contentType=" + contentType
                        + " acceptRanges=" + acceptRanges
                        + " contentLength=" + contentLength
                        + " probeLatencyMs=" + probeLatencyMs);
            } catch (IOException probeError) {
                Log.w(TAG, "warmup metadata failed sourceId=" + request.getSourceId()
                        + " url=" + originalUrl, probeError);
            }
        }

        if (request.getTargetLevel().includes(PlaybackWarmupLevel.PROXY_SESSION)
                && !liveStream
                && NetworkStreamWarmupPolicy.shouldUseSeekableProxy(
                new PlaybackRequest(request.getSourceId(), originalUrl, request.isLiveStream()),
                resolvedUrl)) {
            try {
                proxySessionHandle = SeekablePlaybackProxyServer.getInstance().prepareSession(
                        request.getSourceId(),
                        resolvedUrl,
                        AudioStreamProbeApi.DEFAULT_USER_AGENT);
                localPlaybackUrl = proxySessionHandle.getLocalUrl();
                completedLevel = PlaybackWarmupLevel.PROXY_SESSION;
                Log.d(TAG, "warmup proxy ready sourceId=" + request.getSourceId()
                        + " localUrl=" + localPlaybackUrl
                        + " resolvedUrl=" + resolvedUrl);
            } catch (IOException proxyError) {
                Log.w(TAG, "warmup proxy failed sourceId=" + request.getSourceId()
                        + " url=" + resolvedUrl, proxyError);
            }
        }

        if (request.getTargetLevel().includes(PlaybackWarmupLevel.HEAD_CACHE)
                && proxySessionHandle != null
                && !liveStream) {
            try {
                proxySessionHandle.primeHead(HEAD_CACHE_BYTES);
                completedLevel = request.getTargetLevel().includes(PlaybackWarmupLevel.PLAYBACK_CANDIDATE)
                        ? PlaybackWarmupLevel.PLAYBACK_CANDIDATE
                        : PlaybackWarmupLevel.HEAD_CACHE;
                Log.d(TAG, "warmup head cache ready sourceId=" + request.getSourceId()
                        + " bytes=" + HEAD_CACHE_BYTES);
            } catch (IOException headCacheError) {
                Log.w(TAG, "warmup head cache failed sourceId=" + request.getSourceId()
                        + " url=" + resolvedUrl, headCacheError);
            }
        }

        PlaybackWarmupSnapshot snapshot = buildSnapshot(
                request,
                originalUrl,
                resolvedUrl,
                localPlaybackUrl,
                contentType,
                AudioStreamProbeApi.DEFAULT_USER_AGENT,
                acceptRanges,
                liveStream,
                false,
                seekable,
                contentLength,
                probeLatencyMs,
                completedLevel,
                startedAtMs);
        ResolvedPlayableSource resolvedSource = buildResolvedSource(
                request,
                originalUrl,
                localPlaybackUrl.isEmpty() ? resolvedUrl : localPlaybackUrl,
                contentType,
                liveStream,
                false,
                seekable,
                probeLatencyMs);
        return new PreparedWarmupResult(snapshot, proxySessionHandle, resolvedSource);
    }

    @NonNull
    private PlaybackWarmupSnapshot buildSnapshot(@NonNull PlaybackWarmupRequest request,
                                                 @NonNull String originalUrl,
                                                 @NonNull String resolvedUrl,
                                                 @NonNull String localPlaybackUrl,
                                                 @NonNull String contentType,
                                                 @NonNull String userAgent,
                                                 boolean acceptRanges,
                                                 boolean liveStream,
                                                 boolean localSource,
                                                 boolean seekable,
                                                 long contentLength,
                                                 long probeLatencyMs,
                                                 @NonNull PlaybackWarmupLevel completedLevel,
                                                 long startedAtMs) {
        return new PlaybackWarmupSnapshot(
                request.getSourceId(),
                originalUrl,
                resolvedUrl,
                localPlaybackUrl,
                contentType,
                userAgent,
                request.getTargetLevel(),
                completedLevel,
                liveStream,
                localSource,
                seekable,
                acceptRanges,
                contentLength,
                probeLatencyMs,
                SystemClock.elapsedRealtime() - startedAtMs);
    }

    @NonNull
    private ResolvedPlayableSource buildResolvedSource(@NonNull PlaybackWarmupRequest request,
                                                       @NonNull String originalUrl,
                                                       @NonNull String playbackUrl,
                                                       @NonNull String contentType,
                                                       boolean liveStream,
                                                       boolean localSource,
                                                       boolean seekable,
                                                       long probeLatencyMs) {
        return new ResolvedPlayableSource(
                request.getSourceId(),
                originalUrl,
                playbackUrl,
                contentType,
                AudioStreamProbeApi.DEFAULT_USER_AGENT,
                liveStream,
                localSource,
                seekable,
                probeLatencyMs);
    }

    static final class PreparedWarmupResult {
        private final PlaybackWarmupSnapshot snapshot;
        private final SeekablePlaybackProxyServer.ProxySessionHandle proxySessionHandle;
        private final ResolvedPlayableSource resolvedSource;

        private PreparedWarmupResult(@NonNull PlaybackWarmupSnapshot snapshot,
                                     SeekablePlaybackProxyServer.ProxySessionHandle proxySessionHandle,
                                     @NonNull ResolvedPlayableSource resolvedSource) {
            this.snapshot = snapshot;
            this.proxySessionHandle = proxySessionHandle;
            this.resolvedSource = resolvedSource;
        }

        @NonNull
        PlaybackWarmupSnapshot getSnapshot() {
            return snapshot;
        }

        SeekablePlaybackProxyServer.ProxySessionHandle getProxySessionHandle() {
            return proxySessionHandle;
        }

        @NonNull
        ResolvedPlayableSource getResolvedSource() {
            return resolvedSource;
        }
    }
}
