package com.example.core_recommend;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.feature_player.player.SoLibraryLoader;

import java.io.IOException;

final class RecommendNativeBridge {

    private RecommendNativeBridge() {
    }

    @NonNull
    static String getHomeFeedJson(@NonNull Context context) throws IOException {
        if (!SoLibraryLoader.loadLibrary(context.getApplicationContext())) {
            throw new IOException("Load native recommend library failed");
        }
        String homeFeedJson = nativeGetHomeFeedJson();
        if (homeFeedJson == null || homeFeedJson.isEmpty()) {
            throw new IOException(nativeGetLastError());
        }
        return homeFeedJson;
    }

    @NonNull
    private static native String nativeGetHomeFeedJson();

    @NonNull
    private static native String nativeGetLastError();
}
