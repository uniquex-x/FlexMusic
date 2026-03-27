package com.example.flexmusicplayer.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.player.AudioEffectProfile;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.player.PlaybackController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public final class AudioEffectDialogHelper {

    public interface ISelectionListener {
        void onAudioEffectSelected(@NonNull AudioEffectProfile profile);
    }

    private AudioEffectDialogHelper() {
    }

    public static void showDialog(@NonNull Context context,
                                  @NonNull PlaybackController playbackController,
                                  @Nullable ISelectionListener selectionListener) {
        List<AudioEffectProfile> profiles = playbackController.getAvailableAudioEffectProfiles();
        if (profiles.isEmpty()) {
            return;
        }
        String[] labels = new String[profiles.size()];
        int checkedIndex = 0;
        AudioEffectProfile currentProfile = playbackController.getPlayerState().getAudioEffectProfile();
        for (int index = 0; index < profiles.size(); index++) {
            AudioEffectProfile profile = profiles.get(index);
            labels[index] = resolveLabel(context, profile);
            if (profile == currentProfile) {
                checkedIndex = index;
            }
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.player_audio_effect_title)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    AudioEffectProfile selectedProfile = profiles.get(which);
                    playbackController.setAudioEffectProfile(selectedProfile);
                    if (selectionListener != null) {
                        selectionListener.onAudioEffectSelected(selectedProfile);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    public static String resolveLabel(@NonNull Context context, @NonNull AudioEffectProfile profile) {
        switch (profile) {
            case BASS_BOOST:
                return context.getString(R.string.player_audio_effect_bass_boost);
            case VOCAL_BOOST:
                return context.getString(R.string.player_audio_effect_vocal_boost);
            case TREBLE_BOOST:
                return context.getString(R.string.player_audio_effect_treble_boost);
            case WARM:
                return context.getString(R.string.player_audio_effect_warm);
            case BRIGHT:
                return context.getString(R.string.player_audio_effect_bright);
            case ACOUSTIC:
                return context.getString(R.string.player_audio_effect_acoustic);
            case PODCAST:
                return context.getString(R.string.player_audio_effect_podcast);
            case NIGHT:
                return context.getString(R.string.player_audio_effect_night);
            case OFF:
            default:
                return context.getString(R.string.player_audio_effect_off);
        }
    }
}
