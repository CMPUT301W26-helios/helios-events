package com.example.helios.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class HeliosText {

    private HeliosText() {}

    public static boolean isNonEmpty(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Nullable
    public static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NonNull
    public static String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    public static boolean containsIgnoreCase(@Nullable String value, @NonNull String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }

    @NonNull
    public static String safeLower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.getDefault());
    }
}
