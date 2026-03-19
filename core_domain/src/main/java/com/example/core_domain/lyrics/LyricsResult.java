package com.example.core_domain.lyrics;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LyricsResult {

    private final boolean synced;
    private final String providerId;
    private final List<LyricsLineData> lines;

    public LyricsResult(boolean synced,
                        @NonNull String providerId,
                        @NonNull List<LyricsLineData> lines) {
        this.synced = synced;
        this.providerId = providerId;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    @NonNull
    public static LyricsResult empty(@NonNull String providerId) {
        return new LyricsResult(false, providerId, Collections.emptyList());
    }

    public boolean isSynced() {
        return synced;
    }

    @NonNull
    public String getProviderId() {
        return providerId;
    }

    @NonNull
    public List<LyricsLineData> getLines() {
        return lines;
    }
}
