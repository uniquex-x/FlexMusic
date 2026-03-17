package com.example.flexmusicplayer.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
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

    private SwitchCompat darkModeSwitch;
    private TextView languageValue;
    private TextView cacheSizeValue;
    private MaterialButtonToggleGroup streamingQualityGroup;
    private MaterialButtonToggleGroup playbackModeGroup;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);

        initViews(view);
        loadSettings();
        setupListeners(view);

        return view;
    }

    private void initViews(View view) {
        darkModeSwitch = view.findViewById(R.id.dark_mode_switch);
        languageValue = view.findViewById(R.id.language_value);
        cacheSizeValue = view.findViewById(R.id.cache_size_value);
        streamingQualityGroup = view.findViewById(R.id.streaming_quality_group);
        playbackModeGroup = view.findViewById(R.id.playback_mode_group);
    }

    private void loadSettings() {
        boolean isDark = THEME_DARK.equals(prefs.getString(KEY_THEME, THEME_LIGHT));
        darkModeSwitch.setChecked(isDark);
        languageValue.setText(LANG_ZH.equals(prefs.getString(KEY_LANGUAGE, LANG_EN))
                ? R.string.settings_language_chinese : R.string.settings_language_english);
        cacheSizeValue.setText(R.string.settings_cache_size_mock);
        streamingQualityGroup.check(R.id.quality_high_btn);
        playbackModeGroup.check(R.id.playback_sequential_btn);
    }

    private void setupListeners(View root) {
        root.findViewById(R.id.back_button).setOnClickListener(v -> requireActivity().onBackPressed());
        root.findViewById(R.id.language_item).setOnClickListener(v -> showLanguageDialog());
        root.findViewById(R.id.clear_cache_item).setOnClickListener(v -> showClearCacheDialog());
        root.findViewById(R.id.equalizer_item).setOnClickListener(v -> showPlaceholder(root));
        root.findViewById(R.id.download_quality_item).setOnClickListener(v -> showPlaceholder(root));
        root.findViewById(R.id.storage_path_item).setOnClickListener(v -> showPlaceholder(root));
        root.findViewById(R.id.privacy_policy_item).setOnClickListener(v -> showPlaceholder(root));
        root.findViewById(R.id.user_agreement_item).setOnClickListener(v -> showPlaceholder(root));

        MaterialButton logoutButton = root.findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> showPlaceholder(root));

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                applyTheme(isChecked ? THEME_DARK : THEME_LIGHT));
    }

    private void applyTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
        if (THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void showLanguageDialog() {
        String[] languages = {getString(R.string.settings_language_english), getString(R.string.settings_language_chinese)};
        int checked = LANG_ZH.equals(prefs.getString(KEY_LANGUAGE, LANG_EN)) ? 1 : 0;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(languages, checked, (dialog, which) -> {
                    String lang = which == 1 ? LANG_ZH : LANG_EN;
                    prefs.edit().putString(KEY_LANGUAGE, lang).apply();
                    AppCompatDelegate.setApplicationLocales(LANG_ZH.equals(lang)
                            ? LocaleListCompat.forLanguageTags("zh-CN")
                            : LocaleListCompat.forLanguageTags("en"));
                    languageValue.setText(languages[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_cache_title)
                .setMessage(R.string.dialog_clear_cache_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> cacheSizeValue.setText(R.string.settings_cache_size_empty))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPlaceholder(View root) {
        Snackbar.make(root, R.string.settings_placeholder_message, Snackbar.LENGTH_SHORT).show();
    }
}
