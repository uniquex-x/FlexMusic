package com.example.feature_download;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class DownloadCatalogStore {

    private static final String PREFS_NAME = "flexmusic_download_catalog";
    private static final String KEY_RECORDS = "records";

    private static final String FIELD_SOURCE_ID = "sourceId";
    private static final String FIELD_SOURCE_URL = "sourceUrl";
    private static final String FIELD_LOCAL_PATH = "localPath";
    private static final String FIELD_FILE_NAME = "fileName";
    private static final String FIELD_DOWNLOADED_AT_MS = "downloadedAtMs";

    private final SharedPreferences preferences;

    DownloadCatalogStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    synchronized List<DownloadRecord> loadRecords() {
        List<DownloadRecord> records = new ArrayList<>();
        String raw = preferences.getString(KEY_RECORDS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                DownloadRecord record = fromJson(object);
                if (record != null) {
                    records.add(record);
                }
            }
        } catch (JSONException ignored) {
        }
        return records;
    }

    synchronized void upsert(@NonNull DownloadRecord record) {
        List<DownloadRecord> records = loadRecords();
        removeMatching(records, record.getSourceId(), record.getSourceUrl());
        records.add(record);
        saveRecords(records);
    }

    synchronized void remove(@NonNull String sourceId, @NonNull String sourceUrl) {
        List<DownloadRecord> records = loadRecords();
        removeMatching(records, sourceId, sourceUrl);
        saveRecords(records);
    }

    synchronized void replaceAll(@NonNull List<DownloadRecord> records) {
        saveRecords(records);
    }

    private void removeMatching(@NonNull List<DownloadRecord> records,
                                @NonNull String sourceId,
                                @NonNull String sourceUrl) {
        for (Iterator<DownloadRecord> iterator = records.iterator(); iterator.hasNext(); ) {
            DownloadRecord record = iterator.next();
            if (matches(record, sourceId, sourceUrl)) {
                iterator.remove();
            }
        }
    }

    private boolean matches(@NonNull DownloadRecord record,
                            @NonNull String sourceId,
                            @NonNull String sourceUrl) {
        if (!TextUtils.isEmpty(sourceId) && sourceId.equals(record.getSourceId())) {
            return true;
        }
        return !TextUtils.isEmpty(sourceUrl) && sourceUrl.equals(record.getSourceUrl());
    }

    private void saveRecords(@NonNull List<DownloadRecord> records) {
        JSONArray array = new JSONArray();
        for (DownloadRecord record : records) {
            array.put(toJson(record));
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply();
    }

    @NonNull
    private JSONObject toJson(@NonNull DownloadRecord record) {
        JSONObject object = new JSONObject();
        try {
            object.put(FIELD_SOURCE_ID, record.getSourceId());
            object.put(FIELD_SOURCE_URL, record.getSourceUrl());
            object.put(FIELD_LOCAL_PATH, record.getLocalPath());
            object.put(FIELD_FILE_NAME, record.getFileName());
            object.put(FIELD_DOWNLOADED_AT_MS, record.getDownloadedAtMs());
        } catch (JSONException ignored) {
        }
        return object;
    }

    private DownloadRecord fromJson(@NonNull JSONObject object) {
        String sourceId = object.optString(FIELD_SOURCE_ID, "");
        String sourceUrl = object.optString(FIELD_SOURCE_URL, "");
        String localPath = object.optString(FIELD_LOCAL_PATH, "");
        String fileName = object.optString(FIELD_FILE_NAME, "");
        long downloadedAtMs = object.optLong(FIELD_DOWNLOADED_AT_MS, 0L);
        if (TextUtils.isEmpty(sourceId) || TextUtils.isEmpty(sourceUrl) || TextUtils.isEmpty(localPath)) {
            return null;
        }
        if (TextUtils.isEmpty(fileName)) {
            fileName = sourceId;
        }
        return new DownloadRecord(sourceId, sourceUrl, localPath, fileName, downloadedAtMs);
    }
}
