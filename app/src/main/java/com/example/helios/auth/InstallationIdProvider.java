package com.example.helios.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Utility class that persists a locally generated installation identifier in SharedPreferences.
 *
 * Role: device identity helper used to associate the current installation with a profile or admin device record.
 * Outstanding issues: installation IDs are device-local only and are not protected against app data clearing.
 */
public final class InstallationIdProvider {
    private static final String PREFS = "app_prefs";
    private static final String KEY_INSTALLATION_ID = "installation_id";

    private InstallationIdProvider() {
    }

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

    public static void clearInstallationId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_INSTALLATION_ID).apply();
    }
}