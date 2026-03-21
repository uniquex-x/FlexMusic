package com.example.feature_transcode;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.feature_player.player.SoLibraryLoader;

import java.io.IOException;

public final class TranscodeBridge {

    public static final int OUTPUT_FORMAT_MP3 = 0;
    public static final int OUTPUT_FORMAT_FLAC = 1;
    public static final int OUTPUT_FORMAT_OGG = 2;
    public static final int OUTPUT_FORMAT_WAV = 3;

    private TranscodeBridge() {
    }

    public static void transcode(@NonNull Context context,
                                 @NonNull String sourcePath,
                                 @NonNull String targetPath,
                                 int outputFormat,
                                 int bitrateKbps,
                                 int sampleRate) throws IOException {
        if (!SoLibraryLoader.loadLibrary(context.getApplicationContext())) {
            throw new IOException("Load native transcode libraries failed");
        }
        int result = nativeTranscode(sourcePath, targetPath, outputFormat, bitrateKbps, sampleRate);
        if (result != 0) {
            throw new IOException(nativeGetLastError());
        }
    }

    private static native int nativeTranscode(@NonNull String sourcePath,
                                              @NonNull String targetPath,
                                              int outputFormat,
                                              int bitrateKbps,
                                              int sampleRate);

    @NonNull
    private static native String nativeGetLastError();
}
