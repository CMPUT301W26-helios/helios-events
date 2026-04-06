package com.example.helios.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public class HeliosUiPreferences {
    private static final String PREFS_NAME = "helios_ui_preferences";
    private static final String KEY_SHOW_HEADER_ICON = "show_header_icon";

    private final SharedPreferences preferences;

    public HeliosUiPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isHeaderIconEnabled() {
        return preferences.getBoolean(KEY_SHOW_HEADER_ICON, true);
    }

    public void setHeaderIconEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SHOW_HEADER_ICON, enabled).apply();
    }
}
