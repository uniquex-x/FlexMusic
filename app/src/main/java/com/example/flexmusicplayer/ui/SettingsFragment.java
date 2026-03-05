package com.example.flexmusicplayer.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.model.TranscodeSettings;
import com.google.android.material.button.MaterialButton;

public class SettingsFragment extends Fragment {

    // Audio Settings
    private View audioQualityItem;
    private View equalizerItem;
    private View crossfadeItem;
    private TextView audioQualityValue;
    private TextView crossfadeValue;

    // Transcode Settings
    private SwitchCompat transcodeEnabledSwitch;
    private View transcodeFormatItem;
    private View transcodeBitrateItem;
    private View transcodeSampleRateItem;
    private TextView transcodeFormatValue;
    private TextView transcodeBitrateValue;
    private TextView transcodeSampleRateValue;

    // Download Settings
    private SwitchCompat downloadEnabledSwitch;
    private SwitchCompat wifiOnlySwitch;
    private TextView cacheSizeValue;
    private MaterialButton clearCacheButton;

    // Appearance Settings
    private View themeItem;
    private View languageItem;
    private TextView themeValue;
    private TextView languageValue;

    // About
    private View rateAppItem;
    private View feedbackItem;

    private TranscodeSettings transcodeSettings;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        initViews(view);
        loadSettings();
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        // Audio Settings
        audioQualityItem = view.findViewById(R.id.audio_quality_item);
        equalizerItem = view.findViewById(R.id.equalizer_item);
        crossfadeItem = view.findViewById(R.id.crossfade_item);
        audioQualityValue = view.findViewById(R.id.audio_quality_value);
        crossfadeValue = view.findViewById(R.id.crossfade_value);

        // Transcode Settings
        transcodeEnabledSwitch = view.findViewById(R.id.transcode_enabled_switch);
        transcodeFormatItem = view.findViewById(R.id.transcode_format_item);
        transcodeBitrateItem = view.findViewById(R.id.transcode_bitrate_item);
        transcodeSampleRateItem = view.findViewById(R.id.transcode_sample_rate_item);
        transcodeFormatValue = view.findViewById(R.id.transcode_format_value);
        transcodeBitrateValue = view.findViewById(R.id.transcode_bitrate_value);
        transcodeSampleRateValue = view.findViewById(R.id.transcode_sample_rate_value);

        // Download Settings
        downloadEnabledSwitch = view.findViewById(R.id.download_enabled_switch);
        wifiOnlySwitch = view.findViewById(R.id.wifi_only_switch);
        cacheSizeValue = view.findViewById(R.id.cache_size_value);
        clearCacheButton = view.findViewById(R.id.clear_cache_button);

        // Appearance Settings
        themeItem = view.findViewById(R.id.theme_item);
        languageItem = view.findViewById(R.id.language_item);
        themeValue = view.findViewById(R.id.theme_value);
        languageValue = view.findViewById(R.id.language_value);

        // About
        rateAppItem = view.findViewById(R.id.rate_app_item);
        feedbackItem = view.findViewById(R.id.feedback_item);

        // Initialize transcode settings
        transcodeSettings = new TranscodeSettings();
    }

    private void loadSettings() {
        // TODO: Load actual settings from SharedPreferences

        // Audio Quality
        audioQualityValue.setText("High");
        crossfadeValue.setText("Off");

        // Transcode Settings
        transcodeEnabledSwitch.setChecked(transcodeSettings.isEnabled());
        updateTranscodeSettingsUI();

        // Download Settings
        downloadEnabledSwitch.setChecked(true);
        wifiOnlySwitch.setChecked(true);
        cacheSizeValue.setText("0 MB");

        // Appearance
        themeValue.setText("Auto");
        languageValue.setText("System");
    }

    private void setupClickListeners() {
        // Audio Settings
        audioQualityItem.setOnClickListener(v -> showAudioQualityDialog());
        equalizerItem.setOnClickListener(v -> {
            // TODO: Open equalizer
        });
        crossfadeItem.setOnClickListener(v -> showCrossfadeDialog());

        // Transcode Settings
        transcodeEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            transcodeSettings.setEnabled(isChecked);
            updateTranscodeSettingsUI();
            saveTranscodeSettings();
        });

        transcodeFormatItem.setOnClickListener(v -> showTranscodeFormatDialog());
        transcodeBitrateItem.setOnClickListener(v -> showTranscodeBitrateDialog());
        transcodeSampleRateItem.setOnClickListener(v -> showTranscodeSampleRateDialog());

        // Download Settings
        downloadEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Save download enabled setting
        });

        wifiOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Save wifi only setting
        });

        clearCacheButton.setOnClickListener(v -> showClearCacheDialog());

        // Appearance Settings
        themeItem.setOnClickListener(v -> showThemeDialog());
        languageItem.setOnClickListener(v -> {
            // TODO: Show language dialog
        });

        // About
        rateAppItem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + requireContext().getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + requireContext().getPackageName()));
                startActivity(intent);
            }
        });

        feedbackItem.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@flexmusic.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "FlexMusic Feedback");
            startActivity(Intent.createChooser(intent, "Send Feedback"));
        });
    }

    private void showAudioQualityDialog() {
        String[] qualities = {"Low", "Medium", "High"};
        int currentSelection = 2; // High

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_audio_quality)
                .setSingleChoiceItems(qualities, currentSelection, (dialog, which) -> {
                    audioQualityValue.setText(qualities[which]);
                    dialog.dismiss();
                    // TODO: Save setting
                })
                .show();
    }

    private void showCrossfadeDialog() {
        String[] options = {"Off", "2s", "4s", "6s", "8s", "10s"};
        int currentSelection = 0; // Off

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_crossfade)
                .setSingleChoiceItems(options, currentSelection, (dialog, which) -> {
                    crossfadeValue.setText(options[which]);
                    dialog.dismiss();
                    // TODO: Save setting
                })
                .show();
    }

    private void updateTranscodeSettingsUI() {
        boolean enabled = transcodeSettings.isEnabled();

        transcodeFormatItem.setEnabled(enabled);
        transcodeFormatItem.setAlpha(enabled ? 1.0f : 0.5f);
        transcodeBitrateItem.setEnabled(enabled);
        transcodeBitrateItem.setAlpha(enabled ? 1.0f : 0.5f);
        transcodeSampleRateItem.setEnabled(enabled);
        transcodeSampleRateItem.setAlpha(enabled ? 1.0f : 0.5f);

        if (enabled) {
            transcodeFormatValue.setText(getFormatDisplayName(transcodeSettings.getOutputFormat()));
            transcodeBitrateValue.setText(transcodeSettings.getBitrate().getDisplayName());
            transcodeSampleRateValue.setText(transcodeSettings.getSampleRate().getDisplayName());
        }
    }

    private String getFormatDisplayName(TranscodeSettings.OutputFormat format) {
        switch (format) {
            case MP3:
                return getString(R.string.settings_transcode_format_mp3);
            case VORBIS:
                return getString(R.string.settings_transcode_format_vorbis);
            case FLAC:
                return getString(R.string.settings_transcode_format_flac);
            default:
                return "MP3";
        }
    }

    private void showTranscodeFormatDialog() {
        String[] formats = {
                getString(R.string.settings_transcode_format_mp3),
                getString(R.string.settings_transcode_format_vorbis),
                getString(R.string.settings_transcode_format_flac)
        };
        int currentSelection = transcodeSettings.getOutputFormat().ordinal();

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transcode_format)
                .setSingleChoiceItems(formats, currentSelection, (dialog, which) -> {
                    TranscodeSettings.OutputFormat format = TranscodeSettings.OutputFormat.values()[which];
                    transcodeSettings.setOutputFormat(format);
                    transcodeFormatValue.setText(formats[which]);
                    dialog.dismiss();
                    saveTranscodeSettings();
                })
                .show();
    }

    private void showTranscodeBitrateDialog() {
        TranscodeSettings.Bitrate[] bitrates = TranscodeSettings.Bitrate.values();
        String[] displayNames = new String[bitrates.length];
        for (int i = 0; i < bitrates.length; i++) {
            displayNames[i] = bitrates[i].getDisplayName();
        }
        int currentSelection = transcodeSettings.getBitrate().ordinal();

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transcode_bitrate)
                .setSingleChoiceItems(displayNames, currentSelection, (dialog, which) -> {
                    transcodeSettings.setBitrate(bitrates[which]);
                    transcodeBitrateValue.setText(displayNames[which]);
                    dialog.dismiss();
                    saveTranscodeSettings();
                })
                .show();
    }

    private void showTranscodeSampleRateDialog() {
        TranscodeSettings.SampleRate[] sampleRates = TranscodeSettings.SampleRate.values();
        String[] displayNames = new String[sampleRates.length];
        for (int i = 0; i < sampleRates.length; i++) {
            displayNames[i] = sampleRates[i].getDisplayName();
        }
        int currentSelection = transcodeSettings.getSampleRate().ordinal();

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_transcode_sample_rate)
                .setSingleChoiceItems(displayNames, currentSelection, (dialog, which) -> {
                    transcodeSettings.setSampleRate(sampleRates[which]);
                    transcodeSampleRateValue.setText(displayNames[which]);
                    dialog.dismiss();
                    saveTranscodeSettings();
                })
                .show();
    }

    private void saveTranscodeSettings() {
        // TODO: Save to SharedPreferences
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_cache_title)
                .setMessage(R.string.dialog_clear_cache_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    // TODO: Clear cache
                    cacheSizeValue.setText("0 MB");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showThemeDialog() {
        String[] themes = {
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_auto)
        };
        int currentSelection = 2; // Auto

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(themes, currentSelection, (dialog, which) -> {
                    themeValue.setText(themes[which]);
                    dialog.dismiss();
                    // TODO: Apply theme
                })
                .show();
    }
}
