package com.example.core_network.stream;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.core_domain.player.PlaybackRequest;

import java.util.Locale;

final class NetworkStreamWarmupPolicy {

    private NetworkStreamWarmupPolicy() {
    }

    static boolean shouldUseSeekableProxy(@NonNull PlaybackRequest request, @NonNull String originalUrl) {
        if (request.isLiveStream()) {
            return false;
        }
        String normalizedUrl = originalUrl.toLowerCase(Locale.ROOT);
        return !(normalizedUrl.contains(".m3u8")
                || normalizedUrl.contains(".m3u")
                || normalizedUrl.contains(".pls")
                || normalizedUrl.contains(".xspf")
                || normalizedUrl.contains("/live")
                || normalizedUrl.contains("playlist"));
    }

    static boolean resolveLiveStreamFlag(@NonNull PlaybackRequest request,
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
}
