package com.example.helios.ui.common;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public final class HeliosUi {

    private HeliosUi() {}

    public static void toast(@NonNull Fragment fragment, @NonNull String message) {
        if (!fragment.isAdded()) {
            return;
        }
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
