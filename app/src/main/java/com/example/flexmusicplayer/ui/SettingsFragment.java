package com.example.flexmusicplayer.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.example.flexmusicplayer.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "FlexMusicPrefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_LANGUAGE = "language";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String LANG_EN = "en";
    private static final String LANG_ZH = "zh";

    // Audio Settings
    private View audioQualityItem;
    private View equalizerItem;
    private View crossfadeItem;
    private TextView audioQualityValue;
    private TextView crossfadeValue;

    // Download Settings
    private SwitchCompat downloadEnabledSwitch;
    private SwitchCompat wifiOnlySwitch;
    private TextView cacheSizeValue;
    private MaterialButton clearCacheButton;

    // Appearance Settings - Theme
    private MaterialButtonToggleGroup themeToggleGroup;
    private TextView themeValue;

    // Appearance Settings - Language
    private MaterialButtonToggleGroup languageToggleGroup;
    private TextView languageValue;

    // About
    private View rateAppItem;
    private View feedbackItem;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);

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

        // Download Settings
        downloadEnabledSwitch = view.findViewById(R.id.download_enabled_switch);
        wifiOnlySwitch = view.findViewById(R.id.wifi_only_switch);
        cacheSizeValue = view.findViewById(R.id.cache_size_value);
        clearCacheButton = view.findViewById(R.id.clear_cache_button);

        // Appearance Settings - Theme
        themeToggleGroup = view.findViewById(R.id.theme_toggle_group);
        themeValue = view.findViewById(R.id.theme_value);

        // Appearance Settings - Language
        languageToggleGroup = view.findViewById(R.id.language_toggle_group);
        languageValue = view.findViewById(R.id.language_value);

        // About
        rateAppItem = view.findViewById(R.id.rate_app_item);
        feedbackItem = view.findViewById(R.id.feedback_item);
    }

    private void loadSettings() {
        // Audio
        audioQualityValue.setText("High");
        crossfadeValue.setText("Off");

        // Download
        downloadEnabledSwitch.setChecked(prefs.getBoolean("download_enabled", true));
        wifiOnlySwitch.setChecked(prefs.getBoolean("wifi_only", true));
        cacheSizeValue.setText("0 MB");

        // Theme
        String savedTheme = prefs.getString(KEY_THEME, THEME_LIGHT);
        if (THEME_DARK.equals(savedTheme)) {
            themeToggleGroup.check(R.id.theme_dark_btn);
            themeValue.setText(R.string.settings_theme_dark);
        } else {
            themeToggleGroup.check(R.id.theme_light_btn);
            themeValue.setText(R.string.settings_theme_light);
        }

        // Language
        String savedLang = prefs.getString(KEY_LANGUAGE, LANG_EN);
        if (LANG_ZH.equals(savedLang)) {
            languageToggleGroup.check(R.id.lang_chinese_btn);
            languageValue.setText(R.string.settings_language_chinese);
        } else {
            languageToggleGroup.check(R.id.lang_english_btn);
            languageValue.setText(R.string.settings_language_english);
        }
    }

    private void setupClickListeners() {
        // Audio
        audioQualityItem.setOnClickListener(v -> showAudioQualityDialog());
        equalizerItem.setOnClickListener(v -> { /* TODO: Open equalizer */ });
        crossfadeItem.setOnClickListener(v -> showCrossfadeDialog());

        // Download
        downloadEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("download_enabled", isChecked).apply());
        wifiOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("wifi_only", isChecked).apply());
        clearCacheButton.setOnClickListener(v -> showClearCacheDialog());

        // Theme toggle
        themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.theme_light_btn) {
                applyTheme(THEME_LIGHT);
            } else if (checkedId == R.id.theme_dark_btn) {
                applyTheme(THEME_DARK);
            }
        });

        // Language toggle
        languageToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.lang_english_btn) {
                applyLanguage(LANG_EN);
            } else if (checkedId == R.id.lang_chinese_btn) {
                applyLanguage(LANG_ZH);
            }
        });

        // About
        rateAppItem.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + requireContext().getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id="
                                + requireContext().getPackageName())));
            }
        });

        feedbackItem.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@flexmusic.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "FlexMusic Feedback");
            startActivity(Intent.createChooser(intent, "Send Feedback"));
        });
    }

    /** 应用主题：Light 或 Dark */
    private void applyTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
        if (THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            themeValue.setText(R.string.settings_theme_dark);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            themeValue.setText(R.string.settings_theme_light);
        }
    }

    /** 应用语言：en（English）或 zh（简体中文） */
    private void applyLanguage(String lang) {
        String previousLang = prefs.getString(KEY_LANGUAGE, LANG_EN);
        if (previousLang.equals(lang)) return;

        prefs.edit().putString(KEY_LANGUAGE, lang).apply();

        LocaleListCompat appLocale = LANG_ZH.equals(lang)
                ? LocaleListCompat.forLanguageTags("zh-CN")
                : LocaleListCompat.forLanguageTags("en");
        AppCompatDelegate.setApplicationLocales(appLocale);

        if (LANG_ZH.equals(lang)) {
            languageValue.setText(R.string.settings_language_chinese);
        } else {
            languageValue.setText(R.string.settings_language_english);
        }

        Snackbar.make(requireView(), R.string.settings_language_changed, Snackbar.LENGTH_LONG).show();
    }

    private void showAudioQualityDialog() {
        String[] qualities = {"Low", "Medium", "High"};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_audio_quality)
                .setSingleChoiceItems(qualities, 2, (dialog, which) -> {
                    audioQualityValue.setText(qualities[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showCrossfadeDialog() {
        String[] options = {"Off", "2s", "4s", "6s", "8s", "10s"};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_crossfade)
                .setSingleChoiceItems(options, 0, (dialog, which) -> {
                    crossfadeValue.setText(options[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_clear_cache)
                .setMessage("Clear all cached data?")
                .setPositiveButton(R.string.confirm, (dialog, which) -> cacheSizeValue.setText("0 MB"))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
