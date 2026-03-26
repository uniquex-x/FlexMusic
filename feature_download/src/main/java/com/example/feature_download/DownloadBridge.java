package com.example.feature_download;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.feature_player.player.SoLibraryLoader;

import java.io.IOException;

public final class DownloadBridge {

    private DownloadBridge() {
    }

    public static void download(@NonNull Context context,
                                @NonNull String sourceId,
                                @NonNull String sourceUrl,
                                @NonNull String userAgent,
                                @NonNull String targetPath) throws IOException {
        if (!SoLibraryLoader.loadLibrary(context.getApplicationContext())) {
            throw new IOException("Load native download library failed");
        }
        int result = nativeDownload(sourceId, sourceUrl, userAgent, targetPath);
        if (result != 0) {
            throw new IOException(nativeGetLastError());
        }
    }

    private static native int nativeDownload(@NonNull String sourceId,
                                             @NonNull String sourceUrl,
                                             @NonNull String userAgent,
                                             @NonNull String targetPath);

    @NonNull
    private static native String nativeGetLastError();
}
