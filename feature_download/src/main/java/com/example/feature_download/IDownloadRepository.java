package com.example.feature_download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Repository contract for app-managed offline audio downloads.
 *
 * <p>Implementations own the persistent download catalog, configured quality,
 * and transfer execution boundary between Java and native code.</p>
 */
public interface IDownloadRepository {

    /**
     * Returns every valid persisted download record.
     *
     * @return A filtered snapshot containing only records whose target files still exist.
     */
    @NonNull
    List<DownloadRecord> listRecords();

    /**
     * Looks up an existing download using the source identity first and the original source URL as fallback.
     *
     * @param sourceId Stable logical source identifier for the track.
     * @param sourceUrl Original source URL or URI used before download.
     * @return The persisted record when a valid local file exists, otherwise {@code null}.
     */
    @Nullable
    DownloadRecord findRecord(@NonNull String sourceId, @NonNull String sourceUrl);

    /**
     * Downloads the source into the managed offline directory.
     *
     * @param request Download metadata including source identity and source URL.
     * @return The resulting persisted download record.
     * @throws IOException When the source is unsupported, unavailable, or the local file cannot be written.
     */
    @NonNull
    DownloadRecord download(@NonNull DownloadRequest request) throws IOException;

    /**
     * Returns the currently configured download quality.
     *
     * @return Persisted quality preference.
     */
    @NonNull
    DownloadQuality getPreferredQuality();

    /**
     * Persists the configured download quality.
     *
     * @param quality Target preference to store.
     */
    void setPreferredQuality(@NonNull DownloadQuality quality);

    /**
     * Returns the managed offline directory.
     *
     * @return Existing download root directory.
     * @throws IOException When the directory cannot be created.
     */
    @NonNull
    File getDownloadDirectory() throws IOException;
}
