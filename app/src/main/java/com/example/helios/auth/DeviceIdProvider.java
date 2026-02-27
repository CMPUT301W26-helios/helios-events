package com.example.helios.auth;
import android.content.Context;
import android.provider.Settings;
public class DeviceIdProvider {
    public static String getDeviceId(Context context) {
        return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
    }

}
