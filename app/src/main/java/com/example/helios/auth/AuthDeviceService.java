package com.example.helios.auth;
import android.content.Context;
import android.content.SharedPreferences;
public class AuthDeviceService {
    private static final String PREFS = "app_prefs";
    private static final String KEY_REGISTERED = "registered";

    public static boolean isRegistered(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_REGISTERED, false);
    }

    public static void setRegistered(Context context, boolean value) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_REGISTERED, value).apply();
    }
}
