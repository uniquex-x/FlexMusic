package com.example.core_domain.player;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum AudioEffectProfile {
    OFF("off", 0),
    BASS_BOOST("bass_boost", 1),
    VOCAL_BOOST("vocal_boost", 2),
    TREBLE_BOOST("treble_boost", 3),
    WARM("warm", 4),
    BRIGHT("bright", 5),
    ACOUSTIC("acoustic", 6),
    PODCAST("podcast", 7),
    NIGHT("night", 8);

    private static final List<AudioEffectProfile> AVAILABLE_PROFILES =
            Collections.unmodifiableList(Arrays.asList(values()));

    private final String stableId;
    private final int nativeValue;

    AudioEffectProfile(@NonNull String stableId, int nativeValue) {
        this.stableId = stableId;
        this.nativeValue = nativeValue;
    }

    @NonNull
    public String getStableId() {
        return stableId;
    }

    public int getNativeValue() {
        return nativeValue;
    }

    @NonNull
    public static List<AudioEffectProfile> getAvailableProfiles() {
        return AVAILABLE_PROFILES;
    }

    @NonNull
    public static AudioEffectProfile fromNativeValue(int nativeValue) {
        for (AudioEffectProfile profile : values()) {
            if (profile.nativeValue == nativeValue) {
                return profile;
            }
        }
        return OFF;
    }

    @NonNull
    public static AudioEffectProfile fromStableId(@NonNull String stableId) {
        for (AudioEffectProfile profile : values()) {
            if (profile.stableId.equals(stableId)) {
                return profile;
            }
        }
        return OFF;
    }
}
