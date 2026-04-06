package com.example.helios.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.helios.HeliosApplication;
import com.example.helios.service.AccessibilityPreferences;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

public final class HeliosChipFactory {
    private HeliosChipFactory() {}

    private static final float CHIP_MIN_HEIGHT_DP = 36f;
    private static final float CHIP_COMFORTABLE_MIN_HEIGHT_DP = 48f;
    private static final float CHIP_HORIZONTAL_PADDING_DP = 12f;
    private static final float CHIP_COMFORTABLE_HORIZONTAL_PADDING_DP = 16f;
    private static final float CHIP_ICON_END_PADDING_DP = 8f;

    @NonNull
    public static Chip createCheckableChip(
            @NonNull Context context,
            @NonNull CharSequence text,
            boolean checked
    ) {
        Chip chip = new Chip(context);
        applyBaseStyle(context, chip);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setCheckedIconVisible(true);
        chip.setChipBackgroundColor(createCheckedStateList(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorSurfaceVariant
        ));
        chip.setTextColor(createCheckedStateList(
                context,
                com.google.android.material.R.attr.colorOnPrimary,
                com.google.android.material.R.attr.colorOnSurface
        ));
        chip.setChipStrokeColor(createCheckedStateList(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorOutline
        ));
        chip.setChecked(checked);
        return chip;
    }

    @NonNull
    public static Chip createAssistChip(@NonNull Context context, @NonNull CharSequence text) {
        Chip chip = new Chip(context);
        applyBaseStyle(context, chip);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setCheckable(false);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setCloseIconVisible(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant)
        ));
        chip.setTextColor(MaterialColors.getColor(
                chip,
                com.google.android.material.R.attr.colorOnSurface
        ));
        chip.setChipStrokeColor(ColorStateList.valueOf(
                MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOutline)
        ));
        return chip;
    }

    @NonNull
    public static Chip createDismissibleChip(
            @NonNull Context context,
            @NonNull CharSequence text,
            @NonNull View.OnClickListener closeListener
    ) {
        Chip chip = createAssistChip(context, text);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(closeListener);
        return chip;
    }

    @NonNull
    private static ColorStateList createCheckedStateList(
            @NonNull Context context,
            int checkedAttr,
            int defaultAttr
    ) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        MaterialColors.getColor(context, checkedAttr, 0),
                        MaterialColors.getColor(context, defaultAttr, 0)
                }
        );
    }

    private static void applyBaseStyle(@NonNull Context context, @NonNull Chip chip) {
        float density = context.getResources().getDisplayMetrics().density;
        AccessibilityPreferences accessibilityPreferences =
                HeliosApplication.from(context).getAccessibilityPreferences();
        boolean largeTouchTargets = accessibilityPreferences.isLargeTouchTargetsMode();
        int horizontalPaddingPx = Math.round(
                (largeTouchTargets ? CHIP_COMFORTABLE_HORIZONTAL_PADDING_DP : CHIP_HORIZONTAL_PADDING_DP)
                        * density
        );
        chip.setChipMinHeight(
                (largeTouchTargets ? CHIP_COMFORTABLE_MIN_HEIGHT_DP : CHIP_MIN_HEIGHT_DP) * density
        );
        chip.setChipStrokeWidth(density);
        chip.setChipStartPadding(horizontalPaddingPx);
        chip.setChipEndPadding(horizontalPaddingPx);
        chip.setTextStartPadding(0f);
        chip.setTextEndPadding(0f);
        chip.setIconEndPadding(CHIP_ICON_END_PADDING_DP * density);
    }
}
