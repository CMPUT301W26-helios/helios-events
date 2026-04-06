package com.example.helios.ui.theme;

import androidx.annotation.NonNull;

import com.example.helios.R;

public enum HeliosThemeOption {
    MEADOW("meadow", "Meadow", "Soft green tones.", R.style.Theme_Helios_Meadow),
    MIDNIGHT("midnight", "Midnight", "Dark green tones.", R.style.Theme_Helios_Midnight),
    SUNRISE("sunrise", "Sunrise", "Warm amber tones.", R.style.Theme_Helios_Sunrise),
    OCEAN("ocean", "Ocean", "Cool blue tones.", R.style.Theme_Helios_Ocean),
    BLOSSOM("blossom", "Blossom", "Soft rose tones.", R.style.Theme_Helios_Blossom);

    @NonNull
    private final String storageValue;
    @NonNull
    private final String displayName;
    @NonNull
    private final String description;
    private final int themeResId;

    HeliosThemeOption(
            @NonNull String storageValue,
            @NonNull String displayName,
            @NonNull String description,
            int themeResId
    ) {
        this.storageValue = storageValue;
        this.displayName = displayName;
        this.description = description;
        this.themeResId = themeResId;
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

    public int getThemeResId() {
        return themeResId;
    }

    @NonNull
    public static HeliosThemeOption fromStorageValue(@NonNull String storageValue) {
        for (HeliosThemeOption option : values()) {
            if (option.storageValue.equalsIgnoreCase(storageValue.trim())) {
                return option;
            }
        }
        return MEADOW;
    }
}
