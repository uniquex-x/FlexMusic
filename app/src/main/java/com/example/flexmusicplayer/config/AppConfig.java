package com.example.flexmusicplayer.config;

import androidx.annotation.NonNull;

/**
 * Centralized app-level configuration for environment defaults and feature switches.
 */
public final class AppConfig {

    private AppConfig() {
    }

    public static final class Network {
        private static final String DEFAULT_DOMAIN = "";

        private Network() {
        }

        @NonNull
        public static String getDefaultDomain() {
            return DEFAULT_DOMAIN;
        }
    }

    public static final class Features {
        private static final boolean AUTH_ENABLED = true;     // 登录鉴权功能模块开关
        private static final boolean DOWNLOAD_ENABLED = true;   // 下载模块开关，未实际部署
        private static final boolean TRANSCODE_ENABLED = true;    // 转码模块开关，未实际部署
        private static final boolean ONLINE_SEARCH_ENABLED = true;      // 在线搜索模块开关，未实际部署
        private static final boolean SPOTIFY_SEARCH_ENABLED = false;    // Spotify搜索开关，受开发者配额/订阅策略影响
        private static final boolean SLEEP_RADIO_ENABLED = true;    // sleep模块开关，未实际部署

        private Features() {
        }

        public static boolean isAuthEnabled() {
            return AUTH_ENABLED;
        }

        public static boolean isDownloadEnabled() {
            return DOWNLOAD_ENABLED;
        }

        public static boolean isTranscodeEnabled() {
            return TRANSCODE_ENABLED;
        }

        public static boolean isOnlineSearchEnabled() {
            return ONLINE_SEARCH_ENABLED;
        }

        public static boolean isSpotifySearchEnabled() {
            return SPOTIFY_SEARCH_ENABLED;
        }

        public static boolean isSleepRadioEnabled() {
            return SLEEP_RADIO_ENABLED;
        }
    }
}
