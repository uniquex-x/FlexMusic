package com.example.flexmusicplayer.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.core_domain.player.AudioEffectProfile;
import com.example.feature_download.DownloadQuality;
import com.example.feature_download.DownloadRepository;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.player.PlaybackController;
import com.example.flexmusicplayer.settings.AppLocaleManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "FlexMusicPrefs";
    private static final String KEY_THEME = "theme";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String SLEEP_AUDIO_CACHE_DIRECTORY = "sleep_audio_cache";

    private SwitchCompat darkModeSwitch;
    private TextView languageValue;
    private TextView cacheSizeValue;
    private TextView downloadQualityValue;
    private TextView storagePathValue;
    private TextView equalizerValue;
    private MaterialButtonToggleGroup streamingQualityGroup;
    private MaterialButtonToggleGroup playbackModeGroup;
    private SharedPreferences prefs;
    private Context appContext;
    private DownloadRepository downloadRepository;
    private PlaybackController playbackController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();
    private boolean cacheOperationInFlight;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        appContext = requireContext().getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, 0);
        downloadRepository = new DownloadRepository(appContext);
        playbackController = PlaybackController.getInstance(appContext);

        initViews(view);
        loadSettings();
        setupListeners(view);

        return view;
    }

    private void initViews(View view) {
        darkModeSwitch = view.findViewById(R.id.dark_mode_switch);
        languageValue = view.findViewById(R.id.language_value);
        cacheSizeValue = view.findViewById(R.id.cache_size_value);
        downloadQualityValue = view.findViewById(R.id.download_quality_value);
        storagePathValue = view.findViewById(R.id.storage_path_value);
        equalizerValue = view.findViewById(R.id.equalizer_value);
        streamingQualityGroup = view.findViewById(R.id.streaming_quality_group);
        playbackModeGroup = view.findViewById(R.id.playback_mode_group);
    }

    private void loadSettings() {
        boolean isDark = THEME_DARK.equals(prefs.getString(KEY_THEME, THEME_LIGHT));
        darkModeSwitch.setChecked(isDark);
        languageValue.setText(AppLocaleManager.getLanguageSummaryResId(
                AppLocaleManager.getLanguageSetting(appContext)));
        cacheSizeValue.setText(R.string.loading);
        downloadQualityValue.setText(resolveDownloadQualityLabel(downloadRepository.getPreferredQuality()));
        storagePathValue.setText(resolveStoragePathSummary());
        refreshEqualizerSummary();
        refreshCacheSizeAsync();
        streamingQualityGroup.check(R.id.quality_high_btn);
        playbackModeGroup.check(R.id.playback_sequential_btn);
    }

    private void setupListeners(View root) {
        root.findViewById(R.id.back_button).setOnClickListener(v -> requireActivity().onBackPressed());
        root.findViewById(R.id.language_item).setOnClickListener(v -> showLanguageDialog());
        root.findViewById(R.id.clear_cache_item).setOnClickListener(v -> showClearCacheDialog());
        root.findViewById(R.id.equalizer_item).setOnClickListener(v -> showEqualizerDialog());
        root.findViewById(R.id.download_quality_item).setOnClickListener(v -> showDownloadQualityDialog());
        root.findViewById(R.id.storage_path_item).setOnClickListener(v -> showStoragePathDialog());
        root.findViewById(R.id.privacy_policy_item).setOnClickListener(v -> showPlaceholder(root));
        root.findViewById(R.id.user_agreement_item).setOnClickListener(v -> showPlaceholder(root));

        MaterialButton logoutButton = root.findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> showPlaceholder(root));

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                applyTheme(isChecked ? THEME_DARK : THEME_LIGHT));
    }

    private void applyTheme(String theme) {
        int targetNightMode = THEME_DARK.equals(theme)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() == targetNightMode) {
            return;
        }
        prefs.edit().putString(KEY_THEME, theme).apply();
        AppCompatDelegate.setDefaultNightMode(targetNightMode);
    }

    private void showLanguageDialog() {
        String[] languages = {
                getString(R.string.settings_language_system),
                getString(R.string.settings_language_english),
                getString(R.string.settings_language_chinese)
        };
        String currentLanguage = AppLocaleManager.getLanguageSetting(appContext);
        int checked = resolveLanguageCheckedIndex(currentLanguage);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(languages, checked, (dialog, which) -> {
                    String selectedLanguage = resolveLanguageSetting(which);
                    if (selectedLanguage.equals(currentLanguage)) {
                        dialog.dismiss();
                        return;
                    }
                    dialog.dismiss();
                    AppLocaleManager.updateLanguageSetting(appContext, selectedLanguage);
                    languageValue.setText(languages[which]);
                })
                .show();
    }

    private void showEqualizerDialog() {
        AudioEffectDialogHelper.showDialog(requireContext(), playbackController, selectedProfile -> {
            refreshEqualizerSummary();
            View fragmentView = getView();
            if (fragmentView == null) {
                return;
            }
            Snackbar.make(
                            fragmentView,
                            getString(
                                    R.string.player_audio_effect_applied,
                                    AudioEffectDialogHelper.resolveLabel(requireContext(), selectedProfile)),
                            Snackbar.LENGTH_SHORT)
                    .show();
        });
    }

    private void showDownloadQualityDialog() {
        DownloadQuality[] qualities = DownloadQuality.values();
        String[] labels = new String[qualities.length];
        int checkedIndex = 0;
        DownloadQuality currentQuality = downloadRepository.getPreferredQuality();
        for (int index = 0; index < qualities.length; index++) {
            labels[index] = resolveDownloadQualityLabel(qualities[index]);
            if (qualities[index] == currentQuality) {
                checkedIndex = index;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_download_quality)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    DownloadQuality selectedQuality = qualities[which];
                    if (selectedQuality != currentQuality) {
                        downloadRepository.setPreferredQuality(selectedQuality);
                        downloadQualityValue.setText(labels[which]);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void showStoragePathDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_storage_path)
                .setMessage(resolveStoragePathMessage())
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    private int resolveLanguageCheckedIndex(@NonNull String languageSetting) {
        switch (languageSetting) {
            case AppLocaleManager.LANGUAGE_ENGLISH:
                return 1;
            case AppLocaleManager.LANGUAGE_CHINESE:
                return 2;
            default:
                return 0;
        }
    }

    @NonNull
    private String resolveLanguageSetting(int selectedIndex) {
        switch (selectedIndex) {
            case 1:
                return AppLocaleManager.LANGUAGE_ENGLISH;
            case 2:
                return AppLocaleManager.LANGUAGE_CHINESE;
            default:
                return AppLocaleManager.LANGUAGE_SYSTEM;
        }
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_cache_title)
                .setMessage(R.string.dialog_clear_cache_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> clearCacheAsync())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshCacheSizeAsync() {
        cacheExecutor.execute(() -> {
            long cacheBytes = measureClearableCacheBytes();
            String displayText = formatCacheSize(cacheBytes);
            mainHandler.post(() -> {
                if (!isAdded() || cacheSizeValue == null) {
                    return;
                }
                cacheSizeValue.setText(displayText);
            });
        });
    }

    private void clearCacheAsync() {
        if (cacheOperationInFlight) {
            return;
        }
        cacheOperationInFlight = true;
        cacheSizeValue.setText(R.string.loading);
        cacheExecutor.execute(() -> {
            boolean success = clearClearableCache();
            long remainingBytes = measureClearableCacheBytes();
            String displayText = formatCacheSize(remainingBytes);
            mainHandler.post(() -> {
                cacheOperationInFlight = false;
                if (!isAdded() || getView() == null || cacheSizeValue == null) {
                    return;
                }
                cacheSizeValue.setText(displayText);
                Snackbar.make(
                                getView(),
                                success ? R.string.settings_cache_cleared : R.string.settings_cache_clear_failed,
                                Snackbar.LENGTH_SHORT)
                        .show();
            });
        });
    }

    private long measureClearableCacheBytes() {
        long totalBytes = 0L;
        totalBytes += directorySize(appContext.getCacheDir());
        totalBytes += directorySize(new File(appContext.getFilesDir(), SLEEP_AUDIO_CACHE_DIRECTORY));
        File[] externalCacheDirectories = appContext.getExternalCacheDirs();
        if (externalCacheDirectories != null) {
            for (File directory : externalCacheDirectories) {
                totalBytes += directorySize(directory);
            }
        }
        return totalBytes;
    }

    private boolean clearClearableCache() {
        boolean success = true;
        success &= clearDirectoryContents(appContext.getCacheDir());
        success &= clearDirectoryContents(new File(appContext.getFilesDir(), SLEEP_AUDIO_CACHE_DIRECTORY));
        File[] externalCacheDirectories = appContext.getExternalCacheDirs();
        if (externalCacheDirectories != null) {
            for (File directory : externalCacheDirectories) {
                success &= clearDirectoryContents(directory);
            }
        }
        return success;
    }

    private long directorySize(@Nullable File directory) {
        if (directory == null || !directory.exists()) {
            return 0L;
        }
        if (directory.isFile()) {
            return directory.length();
        }
        long totalBytes = 0L;
        File[] children = directory.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            totalBytes += directorySize(child);
        }
        return totalBytes;
    }

    private boolean clearDirectoryContents(@Nullable File directory) {
        if (directory == null || !directory.exists()) {
            return true;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return true;
        }
        boolean success = true;
        for (File child : children) {
            success &= deleteRecursively(child);
        }
        return success;
    }

    private boolean deleteRecursively(@NonNull File target) {
        boolean success = true;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    success &= deleteRecursively(child);
                }
            }
        }
        if (target.exists() && !target.delete()) {
            success = false;
        }
        return success;
    }

    @NonNull
    private String formatCacheSize(long cacheBytes) {
        if (cacheBytes <= 0L) {
            return getString(R.string.settings_cache_size_empty);
        }
        return getString(R.string.settings_cache_size_value, Formatter.formatShortFileSize(appContext, cacheBytes));
    }

    @NonNull
    private String resolveDownloadQualityLabel(@NonNull DownloadQuality quality) {
        switch (quality) {
            case LOW:
                return getString(R.string.settings_quality_low);
            case MEDIUM:
                return getString(R.string.settings_quality_medium);
            case HIGH:
            default:
                return getString(R.string.settings_quality_high);
        }
    }

    @NonNull
    private String resolveStoragePathSummary() {
        try {
            return downloadRepository.getDownloadDirectory().getAbsolutePath();
        } catch (Exception exception) {
            return getString(R.string.settings_storage_path_value);
        }
    }

    @NonNull
    private String resolveStoragePathMessage() {
        String summary = resolveStoragePathSummary();
        storagePathValue.setText(summary);
        return summary;
    }

    private void refreshEqualizerSummary() {
        if (equalizerValue == null || playbackController == null) {
            return;
        }
        AudioEffectProfile currentProfile = playbackController.getPlayerState().getAudioEffectProfile();
        equalizerValue.setText(AudioEffectDialogHelper.resolveLabel(requireContext(), currentProfile));
    }

    private void showPlaceholder(View root) {
        Snackbar.make(root, R.string.settings_placeholder_message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        cacheExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshEqualizerSummary();
    }
}
