package com.example.feature_download;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DownloadRepository implements IDownloadRepository {

    private static final String TAG = "DownloadRepository";
    private static final int COPY_BUFFER_SIZE = 32 * 1024;
    private static final String DOWNLOAD_DIRECTORY_NAME = "Offline";

    private final Context appContext;
    private final DownloadCatalogStore catalogStore;
    private final DownloadSettingsStore settingsStore;

    public DownloadRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        catalogStore = new DownloadCatalogStore(appContext);
        settingsStore = new DownloadSettingsStore(appContext);
    }

    @NonNull
    @Override
    public List<DownloadRecord> listRecords() {
        List<DownloadRecord> records = catalogStore.loadRecords();
        return filterMissingFiles(records, true);
    }

    @Nullable
    @Override
    public DownloadRecord findRecord(@NonNull String sourceId, @NonNull String sourceUrl) {
        List<DownloadRecord> records = catalogStore.loadRecords();
        boolean changed = false;
        DownloadRecord matchedRecord = null;
        List<DownloadRecord> validRecords = new ArrayList<>();
        for (DownloadRecord record : records) {
            if (new File(record.getLocalPath()).exists()) {
                validRecords.add(record);
                if (matchedRecord == null && matches(record, sourceId, sourceUrl)) {
                    matchedRecord = record;
                }
            } else {
                changed = true;
            }
        }
        if (changed) {
            catalogStore.replaceAll(validRecords);
        }
        return matchedRecord;
    }

    @NonNull
    @Override
    public DownloadRecord download(@NonNull DownloadRequest request) throws IOException {
        validateRequest(request);
        DownloadRecord existingRecord = findRecord(request.getSourceId(), request.getSourceUrl());
        if (existingRecord != null) {
            Log.d(TAG, "download reuse sourceId=" + request.getSourceId()
                    + " path=" + existingRecord.getLocalPath());
            return existingRecord;
        }

        File outputFile = buildOutputFile(request);
        Log.d(TAG, "download start sourceId=" + request.getSourceId()
                + " url=" + request.getSourceUrl()
                + " target=" + outputFile.getAbsolutePath()
                + " quality=" + getPreferredQuality());
        try {
            if (shouldUseJavaCopy(request.getSourceUrl())) {
                copyWithJava(request.getSourceUrl(), outputFile);
            } else {
                DownloadBridge.download(
                        appContext,
                        request.getSourceId(),
                        request.getSourceUrl(),
                        request.getUserAgent(),
                        outputFile.getAbsolutePath());
            }
            DownloadRecord record = new DownloadRecord(
                    request.getSourceId(),
                    request.getSourceUrl(),
                    outputFile.getAbsolutePath(),
                    outputFile.getName(),
                    System.currentTimeMillis());
            catalogStore.upsert(record);
            Log.d(TAG, "download success sourceId=" + request.getSourceId()
                    + " path=" + outputFile.getAbsolutePath()
                    + " bytes=" + outputFile.length());
            return record;
        } catch (IOException ioException) {
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "delete failed path=" + outputFile.getAbsolutePath());
            }
            Log.e(TAG, "download failed sourceId=" + request.getSourceId()
                    + " url=" + request.getSourceUrl(), ioException);
            throw ioException;
        }
    }

    @NonNull
    @Override
    public DownloadQuality getPreferredQuality() {
        return settingsStore.getPreferredQuality();
    }

    @Override
    public void setPreferredQuality(@NonNull DownloadQuality quality) {
        settingsStore.setPreferredQuality(quality);
    }

    @NonNull
    @Override
    public File getDownloadDirectory() throws IOException {
        File root = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (root == null) {
            root = appContext.getFilesDir();
        }
        File downloadDirectory = new File(root, DOWNLOAD_DIRECTORY_NAME);
        if (!downloadDirectory.exists() && !downloadDirectory.mkdirs()) {
            throw new IOException("Create download directory failed");
        }
        return downloadDirectory;
    }

    private void validateRequest(@NonNull DownloadRequest request) throws IOException {
        if (request.isLiveStream()) {
            throw new IOException("Live stream download is not supported");
        }
        if (TextUtils.isEmpty(request.getSourceId())) {
            throw new IOException("Download source id is empty");
        }
        if (TextUtils.isEmpty(request.getSourceUrl())) {
            throw new IOException("Download source url is empty");
        }
    }

    @NonNull
    private List<DownloadRecord> filterMissingFiles(@NonNull List<DownloadRecord> records, boolean saveIfChanged) {
        List<DownloadRecord> validRecords = new ArrayList<>();
        boolean changed = false;
        for (DownloadRecord record : records) {
            if (new File(record.getLocalPath()).exists()) {
                validRecords.add(record);
            } else {
                changed = true;
            }
        }
        if (changed && saveIfChanged) {
            catalogStore.replaceAll(validRecords);
        }
        return validRecords;
    }

    private boolean matches(@NonNull DownloadRecord record,
                            @NonNull String sourceId,
                            @NonNull String sourceUrl) {
        if (!TextUtils.isEmpty(sourceId) && sourceId.equals(record.getSourceId())) {
            return true;
        }
        return !TextUtils.isEmpty(sourceUrl) && sourceUrl.equals(record.getSourceUrl());
    }

    @NonNull
    private File buildOutputFile(@NonNull DownloadRequest request) throws IOException {
        File downloadDirectory = getDownloadDirectory();
        String baseName = sanitizeBaseName(resolveBaseName(request));
        String extension = resolveExtension(request);
        String identityHash = Integer.toHexString((request.getSourceId() + "|" + request.getSourceUrl()).hashCode());
        return new File(downloadDirectory, baseName + "_" + identityHash + extension);
    }

    @NonNull
    private String resolveBaseName(@NonNull DownloadRequest request) {
        if (!TextUtils.isEmpty(request.getDisplayName())) {
            return request.getDisplayName();
        }
        if (!TextUtils.isEmpty(request.getSourceId())) {
            return request.getSourceId();
        }
        return "download";
    }

    @NonNull
    private String sanitizeBaseName(@NonNull String rawValue) {
        String sanitized = rawValue.trim()
                .replace('/', '_')
                .replace('\\', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_');
        if (sanitized.isEmpty()) {
            return "download";
        }
        return sanitized;
    }

    @NonNull
    private String resolveExtension(@NonNull DownloadRequest request) {
        String extension = resolveExtensionFromPath(request.getSourceUrl());
        if (!TextUtils.isEmpty(extension)) {
            return extension;
        }
        extension = resolveExtensionFromQuery(request.getSourceUrl());
        if (!TextUtils.isEmpty(extension)) {
            return extension;
        }
        extension = resolveExtensionFromPath(request.getDisplayName());
        if (!TextUtils.isEmpty(extension)) {
            return extension;
        }
        return ".audio";
    }

    @NonNull
    private String resolveExtensionFromPath(@Nullable String rawPath) {
        if (TextUtils.isEmpty(rawPath)) {
            return "";
        }
        int queryIndex = rawPath.indexOf('?');
        String path = queryIndex >= 0 ? rawPath.substring(0, queryIndex) : rawPath;
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= path.length() - 1) {
            return "";
        }
        String extension = path.substring(dotIndex);
        if (extension.length() > 8) {
            return "";
        }
        for (int index = 1; index < extension.length(); index++) {
            char value = extension.charAt(index);
            if (!Character.isLetterOrDigit(value)) {
                return "";
            }
        }
        return extension.toLowerCase(Locale.US);
    }

    @NonNull
    private String resolveExtensionFromQuery(@Nullable String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) {
            return "";
        }
        try {
            Uri uri = Uri.parse(rawUrl);
            String formatValue = uri.getQueryParameter("format");
            return mapFormatValueToExtension(formatValue);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    @NonNull
    private String mapFormatValueToExtension(@Nullable String formatValue) {
        if (TextUtils.isEmpty(formatValue)) {
            return "";
        }
        String normalizedValue = formatValue.trim().toLowerCase(Locale.US);
        switch (normalizedValue) {
            case "mp3":
            case "mp32":
            case "mp31":
                return ".mp3";
            case "flac":
                return ".flac";
            case "ogg":
            case "vorbis":
                return ".ogg";
            case "aac":
            case "m4a":
                return ".m4a";
            case "wav":
                return ".wav";
            default:
                return "";
        }
    }

    private boolean shouldUseJavaCopy(@NonNull String sourceUrl) {
        Uri uri = Uri.parse(sourceUrl);
        String scheme = uri.getScheme();
        return "content".equalsIgnoreCase(scheme) || "android.resource".equalsIgnoreCase(scheme);
    }

    private void copyWithJava(@NonNull String sourceUrl, @NonNull File targetFile) throws IOException {
        File tempFile = new File(targetFile.getAbsolutePath() + ".download");
        deleteIfExists(tempFile);
        try (InputStream inputStream = openInputStream(sourceUrl);
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            if (inputStream == null) {
                throw new IOException("Open download source failed");
            }
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int count;
            while ((count = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, count);
            }
        } catch (IOException ioException) {
            deleteIfExists(tempFile);
            throw ioException;
        }
        replaceTargetFile(tempFile, targetFile);
    }

    @NonNull
    private InputStream openInputStream(@NonNull String sourceUrl) throws IOException {
        Uri uri = Uri.parse(sourceUrl);
        String scheme = uri.getScheme();
        if (TextUtils.isEmpty(scheme)) {
            return new FileInputStream(new File(sourceUrl));
        }
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (TextUtils.isEmpty(path)) {
                throw new IOException("Resolve file path failed");
            }
            return new FileInputStream(new File(path));
        }
        InputStream inputStream = appContext.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Open input stream failed");
        }
        return inputStream;
    }

    private void replaceTargetFile(@NonNull File tempFile, @NonNull File targetFile) throws IOException {
        deleteIfExists(targetFile);
        if (!tempFile.renameTo(targetFile)) {
            deleteIfExists(tempFile);
            throw new IOException("Move downloaded file failed");
        }
    }

    private void deleteIfExists(@NonNull File file) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "delete failed path=" + file.getAbsolutePath());
        }
    }

    // TODO: Apply the stored quality to source resolution once the network layer exposes multi-bitrate variants.
}
