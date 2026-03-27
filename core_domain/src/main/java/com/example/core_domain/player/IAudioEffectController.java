package com.example.core_domain.player;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Contract for runtime audio-effect selection on the active playback engine.
 *
 * Implementations expose the set of supported effect presets and apply profile
 * changes to the underlying player without requiring the UI layer to know
 * about JNI or native processor details.
 */
public interface IAudioEffectController {

    /**
     * Returns the effect presets supported by the current playback runtime.
     *
     * @return immutable list of selectable audio-effect profiles.
     */
    @NonNull
    List<AudioEffectProfile> getAvailableAudioEffectProfiles();

    /**
     * Returns the profile currently active for subsequent PCM processing.
     *
     * @return selected audio-effect profile, never null.
     */
    @NonNull
    AudioEffectProfile getCurrentAudioEffectProfile();

    /**
     * Applies a new audio-effect profile to the active playback runtime.
     *
     * @param profile target audio-effect profile to apply.
     * @throws IllegalArgumentException when the supplied profile is not supported.
     */
    void setAudioEffectProfile(@NonNull AudioEffectProfile profile);
}
