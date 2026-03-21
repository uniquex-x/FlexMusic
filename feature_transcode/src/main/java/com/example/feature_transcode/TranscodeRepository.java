package com.example.feature_transcode;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class TranscodeRepository {

    private static final String TAG = "TranscodeRepository";
    private static final int COPY_BUFFER_SIZE = 32 * 1024;

    private final Context appContext;

    public TranscodeRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public File transcode(@NonNull Uri sourceUri,
                          @NonNull String displayName,
                          @NonNull TranscodeSettings.OutputFormat outputFormat) throws IOException {
        File stagedInput = stageInputFile(sourceUri, displayName);
        File outputFile = buildOutputFile(displayName, outputFormat);
        Log.d(TAG, "transcode start sourceUri=" + sourceUri
                + " stagedInput=" + stagedInput.getAbsolutePath()
                + " output=" + outputFile.getAbsolutePath()
                + " format=" + outputFormat);
        try {
            TranscodeBridge.transcode(
                    appContext,
                    stagedInput.getAbsolutePath(),
                    outputFile.getAbsolutePath(),
                    toNativeOutputFormat(outputFormat),
                    320,
                    0);
            Log.d(TAG, "transcode success output=" + outputFile.getAbsolutePath());
            return outputFile;
        } catch (IOException ioException) {
            Log.e(TAG, "transcode failed output=" + outputFile.getAbsolutePath(), ioException);
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "delete failed output=" + outputFile.getAbsolutePath());
            }
            throw ioException;
        } finally {
            if (stagedInput.exists() && !stagedInput.delete()) {
                Log.w(TAG, "delete staged input failed path=" + stagedInput.getAbsolutePath());
            }
        }
    }

    @NonNull
    private File stageInputFile(@NonNull Uri sourceUri,
                                @NonNull String displayName) throws IOException {
        File cacheDir = new File(appContext.getCacheDir(), "transcode_input");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Create transcode input cache failed");
        }
        String extension = resolveExtension(displayName);
        File stagedFile = File.createTempFile("transcode_source_", extension, cacheDir);
        try (InputStream inputStream = appContext.getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(stagedFile)) {
            if (inputStream == null) {
                throw new IOException("Open source audio failed");
            }
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int count;
            while ((count = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, count);
            }
        }
        return stagedFile;
    }

    @NonNull
    private File buildOutputFile(@NonNull String displayName,
                                 @NonNull TranscodeSettings.OutputFormat outputFormat) throws IOException {
        File outputRoot = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (outputRoot == null) {
            outputRoot = appContext.getFilesDir();
        }
        File outputDir = new File(outputRoot, "transcoded");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Create transcode output directory failed");
        }
        String baseName = stripExtension(displayName);
        String extension = outputFormat.getFileExtension();
        File candidate = new File(outputDir, baseName + "_converted." + extension);
        if (!candidate.exists()) {
            return candidate;
        }
        int suffix = 2;
        while (true) {
            File nextCandidate = new File(
                    outputDir,
                    String.format(Locale.US, "%s_converted_%d.%s", baseName, suffix, extension));
            if (!nextCandidate.exists()) {
                return nextCandidate;
            }
            suffix++;
        }
    }

    private int toNativeOutputFormat(@NonNull TranscodeSettings.OutputFormat outputFormat) {
        switch (outputFormat) {
            case FLAC:
                return TranscodeBridge.OUTPUT_FORMAT_FLAC;
            case OGG:
                return TranscodeBridge.OUTPUT_FORMAT_OGG;
            case WAV:
                return TranscodeBridge.OUTPUT_FORMAT_WAV;
            case MP3:
            default:
                return TranscodeBridge.OUTPUT_FORMAT_MP3;
        }
    }

    @NonNull
    private String stripExtension(@NonNull String displayName) {
        String sanitized = displayName.trim()
                .replace('/', '_')
                .replace('\\', '_');
        if (sanitized.isEmpty()) {
            return "audio";
        }
        int dotIndex = sanitized.lastIndexOf('.');
        return dotIndex > 0 ? sanitized.substring(0, dotIndex) : sanitized;
    }

    @NonNull
    private String resolveExtension(@NonNull String displayName) {
        int dotIndex = displayName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= displayName.length() - 1) {
            return ".tmp";
        }
        String extension = displayName.substring(dotIndex);
        return TextUtils.isEmpty(extension) ? ".tmp" : extension;
    }
}
