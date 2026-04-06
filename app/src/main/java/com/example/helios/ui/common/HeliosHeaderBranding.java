package com.example.helios.ui.common;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.HeliosApplication;
import com.example.helios.R;

public final class HeliosHeaderBranding {
    private HeliosHeaderBranding() {
    }

    public static void applyToActivity(@NonNull AppCompatActivity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }

        boolean showHeaderIcon = HeliosApplication.from(activity)
                .getUiPreferences()
                .isHeaderIconEnabled();
        applyToView(root, showHeaderIcon);
    }

    public static void applyToView(@NonNull View root, boolean showHeaderIcon) {
        setVisible(root.findViewById(R.id.ivHeaderHeliosIcon), showHeaderIcon);
    }

    private static void setVisible(@Nullable ImageView imageView, boolean visible) {
        if (imageView == null) {
            return;
        }
        imageView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
