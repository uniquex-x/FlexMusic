package com.example.core_network.sleep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.sleep.SleepAudioAsset;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SleepAudioRemoteService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final Map<String, SleepAudioAsset> ASSET_MAP = buildAssetMap();

    @Nullable
    public SleepAudioAsset findAsset(@NonNull String soundId) {
        return ASSET_MAP.get(soundId);
    }

    @NonNull
    public SleepAudioAsset requireAsset(@NonNull String soundId) throws IOException {
        SleepAudioAsset asset = findAsset(soundId);
        if (asset == null) {
            throw new IOException("Unknown sleep audio asset: " + soundId);
        }
        return asset;
    }

    public void downloadToFile(@NonNull SleepAudioAsset asset, @NonNull File targetFile) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(asset.getRemoteUrl()).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "FlexMusic-Sleep/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + " while downloading " + asset.getId());
            }

            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(targetFile, false)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static Map<String, SleepAudioAsset> buildAssetMap() {
        Map<String, SleepAudioAsset> assets = new LinkedHashMap<>();
        assets.put("default_mix", new SleepAudioAsset(
                "default_mix",
                "Default Sleep Mix",
                "https://assets.mixkit.co/active_storage/sfx/1191/1191-preview.mp3",
                "mp3"));
        assets.put("rainfall", new SleepAudioAsset(
                "rainfall",
                "Rainfall",
                "https://assets.mixkit.co/active_storage/sfx/1225/1225-preview.mp3",
                "mp3"));
        assets.put("ocean_waves", new SleepAudioAsset(
                "ocean_waves",
                "Ocean Waves",
                "https://assets.mixkit.co/active_storage/sfx/1196/1196-preview.mp3",
                "mp3"));
        assets.put("night_wind", new SleepAudioAsset(
                "night_wind",
                "Night Wind",
                "https://assets.mixkit.co/active_storage/sfx/2427/2427-preview.mp3",
                "mp3"));
        assets.put("deep_forest", new SleepAudioAsset(
                "deep_forest",
                "Deep Forest",
                "https://assets.mixkit.co/active_storage/sfx/1224/1224-preview.mp3",
                "mp3"));
        return Collections.unmodifiableMap(assets);
    }
}
