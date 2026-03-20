package com.example.flexmusicplayer.sleep;

import androidx.annotation.NonNull;

public enum SleepSound {
    DEFAULT_MIX,
    RAIN,
    OCEAN,
    WIND,
    FOREST;

    @NonNull
    public String getAssetId() {
        if (this == DEFAULT_MIX) {
            return "default_mix";
        }
        if (this == RAIN) {
            return "rainfall";
        }
        if (this == OCEAN) {
            return "ocean_waves";
        }
        if (this == WIND) {
            return "night_wind";
        }
        return "deep_forest";
    }
}
