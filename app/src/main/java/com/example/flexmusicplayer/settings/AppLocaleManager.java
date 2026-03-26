package com.example.flexmusicplayer.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.flexmusicplayer.R;

public final class AppLocaleManager {

    private static final String PREFS_NAME = "FlexMusicPrefs";
    private static final String KEY_LANGUAGE = "language";

    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_CHINESE = "zh";

    private AppLocaleManager() {
    }

    public static void applyStoredLocale(@NonNull Context context) {
        applyLanguageSetting(getLanguageSetting(context));
    }

    public static void updateLanguageSetting(@NonNull Context context, @NonNull String languageSetting) {
        String normalizedSetting = normalizeLanguageSetting(languageSetting);
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_LANGUAGE, normalizedSetting).apply();
        applyLanguageSetting(normalizedSetting);
    }

    @NonNull
    public static String getLanguageSetting(@NonNull Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return normalizeLanguageSetting(preferences.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM));
    }

    public static int getLanguageSummaryResId(@NonNull String languageSetting) {
        switch (normalizeLanguageSetting(languageSetting)) {
            case LANGUAGE_CHINESE:
                return R.string.settings_language_chinese;
            case LANGUAGE_ENGLISH:
                return R.string.settings_language_english;
            default:
                return R.string.settings_language_system;
        }
    }

    @NonNull
    private static String normalizeLanguageSetting(String languageSetting) {
        if (LANGUAGE_CHINESE.equals(languageSetting)) {
            return LANGUAGE_CHINESE;
        }
        if (LANGUAGE_ENGLISH.equals(languageSetting)) {
            return LANGUAGE_ENGLISH;
        }
        return LANGUAGE_SYSTEM;
    }

    private static void applyLanguageSetting(@NonNull String languageSetting) {
        LocaleListCompat targetLocales = buildLocales(languageSetting);
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (TextUtils.equals(currentLocales.toLanguageTags(), targetLocales.toLanguageTags())) {
            return;
        }
        AppCompatDelegate.setApplicationLocales(targetLocales);
    }

    @NonNull
    private static LocaleListCompat buildLocales(@NonNull String languageSetting) {
        switch (normalizeLanguageSetting(languageSetting)) {
            case LANGUAGE_CHINESE:
                return LocaleListCompat.forLanguageTags("zh-CN");
            case LANGUAGE_ENGLISH:
                return LocaleListCompat.forLanguageTags("en");
            default:
                return LocaleListCompat.getEmptyLocaleList();
        }
    }
}
