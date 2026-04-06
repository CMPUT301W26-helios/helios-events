package com.example.helios.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.service.AccessibilityPreferences;

public class HeliosThemeManager {
    private static final String PREFS_NAME = "helios_ui_preferences";
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_FONT = "selected_font";

    private final SharedPreferences preferences;

    public HeliosThemeManager(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public HeliosThemeOption getThemeOption() {
        String storedValue = preferences.getString(KEY_THEME, HeliosThemeOption.MEADOW.getStorageValue());
        if (storedValue == null) {
            return HeliosThemeOption.MEADOW;
        }
        return HeliosThemeOption.fromStorageValue(storedValue);
    }

    public void setThemeOption(@NonNull HeliosThemeOption option) {
        preferences.edit().putString(KEY_THEME, option.getStorageValue()).apply();
    }

    @NonNull
    public HeliosFontOption getFontOption() {
        String storedValue = preferences.getString(KEY_FONT, HeliosFontOption.SANS.getStorageValue());
        if (storedValue == null) {
            return HeliosFontOption.SANS;
        }
        return HeliosFontOption.fromStorageValue(storedValue);
    }

    public void setFontOption(@NonNull HeliosFontOption option) {
        preferences.edit().putString(KEY_FONT, option.getStorageValue()).apply();
    }

    @NonNull
    public Typeface getTypeface() {
        return Typeface.create(getFontOption().getFontFamilyName(), Typeface.NORMAL);
    }

    @NonNull
    public String createAppearanceSignature(@NonNull AccessibilityPreferences accessibilityPreferences) {
        return getThemeOption().getStorageValue()
                + "|font=" + getFontOption().getStorageValue()
                + "|cb=" + accessibilityPreferences.isColorBlindMode()
                + "|lt=" + accessibilityPreferences.isLargeTextMode()
                + "|tt=" + accessibilityPreferences.isLargeTouchTargetsMode();
    }

    public void applyTheme(
            @NonNull AppCompatActivity activity,
            @NonNull AccessibilityPreferences accessibilityPreferences
    ) {
        activity.setTheme(getThemeOption().getThemeResId());
        if (accessibilityPreferences.isColorBlindMode()) {
            activity.getTheme().applyStyle(R.style.Theme_Helios_Accessibility_ColorBlind, true);
        }
        if (accessibilityPreferences.isLargeTextMode()) {
            activity.getTheme().applyStyle(R.style.Theme_Helios_Accessibility_LargeText, true);
        }
        if (accessibilityPreferences.isLargeTouchTargetsMode()) {
            activity.getTheme().applyStyle(R.style.Theme_Helios_Accessibility_LargeTouchTargets, true);
        }
    }
}
