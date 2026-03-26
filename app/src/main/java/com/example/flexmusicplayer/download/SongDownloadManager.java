package com.example.flexmusicplayer.download;

import android.content.Context;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.feature_download.DownloadRecord;
import com.example.feature_download.DownloadRepository;
import com.example.feature_download.DownloadRequest;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.Song;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SongDownloadManager {

    public interface DownloadCallbacks {
        default void onDownloadStateChanged(@NonNull Song song) {
        }

        default void onDownloadSucceeded(@NonNull Song song, boolean alreadyDownloaded) {
        }

        default void onDownloadFailed(@NonNull Song song, @NonNull String message) {
        }
    }

    private static SongDownloadManager instance;

    private final Context appContext;
    private final DownloadRepository downloadRepository;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Set<String> inFlightKeys = new HashSet<>();

    private SongDownloadManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        downloadRepository = new DownloadRepository(appContext);
    }

    @NonNull
    public static synchronized SongDownloadManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new SongDownloadManager(context);
        }
        return instance;
    }

    public void refreshDownloadState(@NonNull Song song) {
        song.setDownloaded(downloadRepository.findRecord(resolveSourceId(song), safeSourceUrl(song)) != null);
    }

    public void refreshDownloadStates(@NonNull List<Song> songs) {
        List<DownloadRecord> records = downloadRepository.listRecords();
        for (Song song : songs) {
            song.setDownloaded(matchesAnyRecord(records, song));
        }
    }

    public boolean isDownloadInFlight(@NonNull Song song) {
        synchronized (lock) {
            return inFlightKeys.contains(buildDownloadKey(song));
        }
    }

    public void requestDownload(@NonNull Song song, @NonNull DownloadCallbacks callbacks) {
        String sourceUrl = safeSourceUrl(song);
        if (TextUtils.isEmpty(sourceUrl)) {
            callbacks.onDownloadFailed(song, appContext.getString(R.string.download_missing_source));
            return;
        }
        if (song.isRadioStream()) {
            callbacks.onDownloadFailed(song, appContext.getString(R.string.download_live_stream_not_supported));
            return;
        }

        String downloadKey = buildDownloadKey(song);
        synchronized (lock) {
            if (inFlightKeys.contains(downloadKey)) {
                callbacks.onDownloadFailed(song, appContext.getString(R.string.download_in_progress_message));
                return;
            }
            inFlightKeys.add(downloadKey);
        }
        callbacks.onDownloadStateChanged(song);

        downloadExecutor.execute(() -> {
            boolean alreadyDownloaded = false;
            String failureMessage = null;
            try {
                DownloadRecord existingRecord = downloadRepository.findRecord(resolveSourceId(song), sourceUrl);
                if (existingRecord != null) {
                    alreadyDownloaded = true;
                    song.setDownloaded(true);
                } else {
                    downloadRepository.download(buildRequest(song));
                    song.setDownloaded(true);
                }
            } catch (IOException ioException) {
                failureMessage = ioException.getMessage();
                if (TextUtils.isEmpty(failureMessage)) {
                    failureMessage = appContext.getString(R.string.download_failed_generic);
                } else {
                    failureMessage = appContext.getString(R.string.download_failed_message, failureMessage);
                }
                refreshDownloadState(song);
            }

            boolean finalAlreadyDownloaded = alreadyDownloaded;
            String finalFailureMessage = failureMessage;
            synchronized (lock) {
                inFlightKeys.remove(downloadKey);
            }
            mainHandler.post(() -> {
                callbacks.onDownloadStateChanged(song);
                if (finalFailureMessage == null) {
                    callbacks.onDownloadSucceeded(song, finalAlreadyDownloaded);
                } else {
                    callbacks.onDownloadFailed(song, finalFailureMessage);
                }
            });
        });
    }

    @NonNull
    private DownloadRequest buildRequest(@NonNull Song song) {
        return new DownloadRequest(
                resolveSourceId(song),
                TextUtils.isEmpty(song.getTitle()) ? resolveSourceId(song) : song.getTitle(),
                safeSourceUrl(song),
                "",
                song.isRadioStream());
    }

    private boolean matchesAnyRecord(@NonNull List<DownloadRecord> records, @NonNull Song song) {
        String sourceId = resolveSourceId(song);
        String sourceUrl = safeSourceUrl(song);
        for (DownloadRecord record : records) {
            if (sourceId.equals(record.getSourceId())) {
                return true;
            }
            if (!TextUtils.isEmpty(sourceUrl) && sourceUrl.equals(record.getSourceUrl())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String buildDownloadKey(@NonNull Song song) {
        return resolveSourceId(song) + "|" + safeSourceUrl(song);
    }

    @NonNull
    private String resolveSourceId(@NonNull Song song) {
        if (!TextUtils.isEmpty(song.getSourceId())) {
            return song.getSourceId();
        }
        return String.valueOf(song.getId());
    }

    @NonNull
    private String safeSourceUrl(@NonNull Song song) {
        return song.getAudioUrl() == null ? "" : song.getAudioUrl();
    }
}
