package com.example.helios.ui.theme;

import androidx.annotation.NonNull;

public enum HeliosFontOption {
    SANS("sans", "Sans", "Clean default UI text.", "sans-serif"),
    SERIF("serif", "Serif", "Classic text with stronger contrast.", "serif"),
    MONO("mono", "Mono", "Uniform-width lettering.", "monospace");

    @NonNull
    private final String storageValue;
    @NonNull
    private final String displayName;
    @NonNull
    private final String description;
    @NonNull
    private final String fontFamilyName;

    HeliosFontOption(
            @NonNull String storageValue,
            @NonNull String displayName,
            @NonNull String description,
            @NonNull String fontFamilyName
    ) {
        this.storageValue = storageValue;
        this.displayName = displayName;
        this.description = description;
        this.fontFamilyName = fontFamilyName;
    }

    @NonNull
    public String getStorageValue() {
        return storageValue;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public String getFontFamilyName() {
        return fontFamilyName;
    }

    @NonNull
    public static HeliosFontOption fromStorageValue(@NonNull String storageValue) {
        for (HeliosFontOption option : values()) {
            if (option.storageValue.equalsIgnoreCase(storageValue.trim())) {
                return option;
            }
        }
        return SANS;
    }
}
