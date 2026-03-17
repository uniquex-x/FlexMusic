package com.example.flexmusicplayer.sleep;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SleepRecentStore {

    private static final String PREFS_NAME = "flexmusic_sleep_recent";
    private static final String KEY_RECENT = "recent_entries";
    private static final int MAX_ENTRIES = 6;

    private final SharedPreferences preferences;

    public SleepRecentStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<SleepRecentEntry> loadRecents() {
        List<SleepRecentEntry> entries = new ArrayList<>();
        String raw = preferences.getString(KEY_RECENT, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                entries.add(fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return entries;
    }

    public void recordAmbience(@NonNull SleepSound sound, @NonNull String title, @NonNull String subtitle) {
        record(new SleepRecentEntry(
                SleepRecentEntry.Type.AMBIENCE,
                sound.name(),
                title,
                subtitle,
                System.currentTimeMillis()));
    }

    public void recordStation(@NonNull SleepRadioStation station) {
        record(new SleepRecentEntry(
                SleepRecentEntry.Type.RADIO,
                station.getId(),
                station.getName(),
                station.getSubtitle(),
                System.currentTimeMillis()));
    }

    private void record(@NonNull SleepRecentEntry entry) {
        List<SleepRecentEntry> entries = loadRecents();
        Iterator<SleepRecentEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            SleepRecentEntry existing = iterator.next();
            if (existing.getType() == entry.getType() && existing.getReferenceId().equals(entry.getReferenceId())) {
                iterator.remove();
            }
        }
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        save(entries);
    }

    private void save(@NonNull List<SleepRecentEntry> entries) {
        JSONArray array = new JSONArray();
        for (SleepRecentEntry entry : entries) {
            array.put(toJson(entry));
        }
        preferences.edit().putString(KEY_RECENT, array.toString()).apply();
    }

    @NonNull
    private JSONObject toJson(@NonNull SleepRecentEntry entry) {
        JSONObject object = new JSONObject();
        try {
            object.put("type", entry.getType().name());
            object.put("referenceId", entry.getReferenceId());
            object.put("title", entry.getTitle());
            object.put("subtitle", entry.getSubtitle());
            object.put("playedAt", entry.getPlayedAt());
        } catch (JSONException ignored) {
        }
        return object;
    }

    @NonNull
    private SleepRecentEntry fromJson(@NonNull JSONObject object) {
        SleepRecentEntry.Type type = SleepRecentEntry.Type.valueOf(
                object.optString("type", SleepRecentEntry.Type.AMBIENCE.name()));
        return new SleepRecentEntry(
                type,
                object.optString("referenceId"),
                object.optString("title"),
                object.optString("subtitle"),
                object.optLong("playedAt"));
    }
}
