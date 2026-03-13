package com.example.helios.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Utility class that persists a locally generated installation identifier in SharedPreferences.
 * Provides a unique, persistent installation ID for the application.
 * This ID is generated once and stored in SharedPreferences.
 *
 * Role: device identity helper used to associate the current installation with a profile or admin device record.
 * Issues: installation IDs are device-local only and are not protected against app data clearing.
 */
public final class InstallationIdProvider {
    private static final String PREFS = "app_prefs";
    private static final String KEY_INSTALLATION_ID = "installation_id";

    private InstallationIdProvider() {
    }

    /**
     * Returns the installation ID for the device. If one does not already exist,
     * a new random UUID is generated, stored, and returned.
     *
     * @param context The application context to access SharedPreferences.
     * @return The unique installation ID for this device.
     */
    public static String getInstallationId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = sp.getString(KEY_INSTALLATION_ID, null);

        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }

        String generated = UUID.randomUUID().toString();
        sp.edit().putString(KEY_INSTALLATION_ID, generated).apply();
        return generated;
    }

    /**
     * Clears the current installation ID from SharedPreferences.
     *
     * @param context The application context to access SharedPreferences.
     */
    public static void clearInstallationId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_INSTALLATION_ID).apply();
    }
}
