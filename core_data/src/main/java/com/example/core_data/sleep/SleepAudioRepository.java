package com.example.core_data.sleep;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.core_domain.sleep.SleepAudioAsset;
import com.example.core_domain.sleep.SleepAudioPlaybackSource;
import com.example.core_network.sleep.SleepAudioRemoteService;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class SleepAudioRepository {

    private static final long MAX_CACHE_BYTES = 80L * 1024L * 1024L;

    private final Object lock = new Object();
    private final File cacheDirectory;
    private final SleepAudioRemoteService remoteService;

    public SleepAudioRepository(@NonNull Context context) {
        this(context, new SleepAudioRemoteService());
    }

    public SleepAudioRepository(@NonNull Context context,
                                @NonNull SleepAudioRemoteService remoteService) {
        this.remoteService = remoteService;
        this.cacheDirectory = new File(context.getApplicationContext().getFilesDir(), "sleep_audio_cache");
    }

    @NonNull
    public SleepAudioPlaybackSource resolvePlaybackSource(@NonNull String soundId) throws IOException {
        SleepAudioAsset asset = remoteService.requireAsset(soundId);
        synchronized (lock) {
            ensureCacheDirectory();
            File cachedFile = resolveCacheFile(asset);
            if (isValidCacheFile(cachedFile)) {
                touch(cachedFile);
                return new SleepAudioPlaybackSource(cachedFile.getAbsolutePath(), true);
            }
        }
        return new SleepAudioPlaybackSource(asset.getRemoteUrl(), false);
    }

    public boolean isCached(@NonNull String soundId) {
        try {
            SleepAudioAsset asset = remoteService.requireAsset(soundId);
            synchronized (lock) {
                ensureCacheDirectory();
                return isValidCacheFile(resolveCacheFile(asset));
            }
        } catch (IOException e) {
            return false;
        }
    }

    public void cacheForOffline(@NonNull String soundId) throws IOException {
        SleepAudioAsset asset = remoteService.requireAsset(soundId);
        File tempFile;
        synchronized (lock) {
            ensureCacheDirectory();
            File cachedFile = resolveCacheFile(asset);
            if (isValidCacheFile(cachedFile)) {
                touch(cachedFile);
                return;
            }
            tempFile = new File(cacheDirectory, asset.getId() + ".download");
            if (tempFile.exists() && !tempFile.delete()) {
                throw new IOException("Unable to replace temporary cache file for " + asset.getId());
            }
        }

        boolean renamed = false;
        try {
            remoteService.downloadToFile(asset, tempFile);
            synchronized (lock) {
                File cachedFile = resolveCacheFile(asset);
                if (cachedFile.exists() && !cachedFile.delete()) {
                    throw new IOException("Unable to replace cached sleep audio for " + asset.getId());
                }
                if (!tempFile.renameTo(cachedFile)) {
                    throw new IOException("Unable to finalize cached sleep audio for " + asset.getId());
                }
                renamed = true;
                touch(cachedFile);
                evictIfNeeded(cachedFile);
            }
        } finally {
            if (!renamed && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @NonNull
    private File resolveCacheFile(@NonNull SleepAudioAsset asset) {
        return new File(cacheDirectory, asset.getId() + "." + asset.getFileExtension());
    }

    private boolean isValidCacheFile(@NonNull File file) {
        return file.exists() && file.isFile() && file.length() > 0L;
    }

    private void touch(@NonNull File file) {
        file.setLastModified(System.currentTimeMillis());
    }

    private void ensureCacheDirectory() throws IOException {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw new IOException("Unable to create sleep audio cache directory");
        }
    }

    private void evictIfNeeded(@NonNull File protectedFile) {
        File[] files = cacheDirectory.listFiles(file -> file.isFile() && !file.getName().endsWith(".download"));
        if (files == null || files.length == 0) {
            return;
        }

        long totalBytes = 0L;
        for (File file : files) {
            totalBytes += file.length();
        }
        if (totalBytes <= MAX_CACHE_BYTES) {
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (totalBytes <= MAX_CACHE_BYTES) {
                break;
            }
            if (file.equals(protectedFile)) {
                continue;
            }
            long fileLength = file.length();
            if (file.delete()) {
                totalBytes -= fileLength;
            }
        }
    }
}
