package com.example.helios.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

public class AccessibilityPreferences {
    private static final String PREFS_NAME = "helios_accessibility";
    private static final String KEY_COLOR_BLIND = "color_blind_mode";
    private static final String KEY_LARGE_TEXT = "large_text_mode";
    private static final String KEY_LARGE_TOUCH_TARGETS = "large_touch_targets_mode";
    private static final float LARGE_TEXT_FONT_SCALE = 1.3f;

    private final SharedPreferences prefs;

    public AccessibilityPreferences(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateLegacyComfortMode();
    }

    public boolean isColorBlindMode() {
        return prefs.getBoolean(KEY_COLOR_BLIND, false);
    }

    public boolean isLargeTextMode() {
        return prefs.getBoolean(KEY_LARGE_TEXT, false);
    }

    public boolean isLargeTouchTargetsMode() {
        return prefs.getBoolean(KEY_LARGE_TOUCH_TARGETS, false);
    }

    public void setColorBlindMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_COLOR_BLIND, enabled).apply();
    }

    public void setLargeTextMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_LARGE_TEXT, enabled).apply();
    }

    public void setLargeTouchTargetsMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_LARGE_TOUCH_TARGETS, enabled).apply();
    }

    @NonNull
    public Context wrapContext(@NonNull Context base) {
        if (!isLargeTextMode()) {
            return base;
        }

        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.fontScale = LARGE_TEXT_FONT_SCALE;
        return base.createConfigurationContext(configuration);
    }

    private void migrateLegacyComfortMode() {
        if (!prefs.contains(KEY_LARGE_TEXT) || prefs.contains(KEY_LARGE_TOUCH_TARGETS)) {
            return;
        }

        boolean legacyCombinedMode = prefs.getBoolean(KEY_LARGE_TEXT, false);
        prefs.edit()
                .putBoolean(KEY_LARGE_TOUCH_TARGETS, legacyCombinedMode)
                .apply();
    }
}
