package com.example.helios.ui.common;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;

import java.util.Locale;

/**
 * Shared helpers for Android foreground location access.
 */
public final class HeliosLocation {

    public static final String[] LOCATION_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private HeliosLocation() {}

    public static boolean hasAnyLocationPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPreciseLocationPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static int getLocationPriority(@NonNull Context context) {
        return hasPreciseLocationPermission(context)
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
    }

    @NonNull
    public static CurrentLocationRequest createCurrentLocationRequest(@NonNull Context context) {
        return new CurrentLocationRequest.Builder()
                .setPriority(getLocationPriority(context))
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setDurationMillis(15_000L)
                .setMaxUpdateAgeMillis(120_000L)
                .build();
    }

    @NonNull
    public static LocationSettingsRequest createLocationSettingsRequest(@NonNull Context context) {
        LocationRequest request = new LocationRequest.Builder(1_000L)
                .setPriority(getLocationPriority(context))
                .build();
        return new LocationSettingsRequest.Builder()
                .addLocationRequest(request)
                .setAlwaysShow(true)
                .build();
    }

    public static boolean isLikelyEmulator() {
        String fingerprint = lower(Build.FINGERPRINT);
        String model = lower(Build.MODEL);
        String product = lower(Build.PRODUCT);
        String manufacturer = lower(Build.MANUFACTURER);
        String brand = lower(Build.BRAND);
        String device = lower(Build.DEVICE);
        return fingerprint.contains("generic")
                || fingerprint.contains("emulator")
                || model.contains("emulator")
                || model.contains("sdk built for")
                || manufacturer.contains("genymotion")
                || brand.startsWith("generic")
                || device.startsWith("generic")
                || product.contains("sdk")
                || product.contains("emulator")
                || product.contains("simulator");
    }

    @NonNull
    public static String buildPermissionDeniedMessage(@NonNull String actionLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append("Location permission is required to ").append(actionLabel)
                .append(". Allow precise or approximate location when Android prompts.");
        if (isLikelyEmulator()) {
            builder.append(" Emulator spoofed locations still require Android location permission.");
        }
        return builder.toString();
    }

    @NonNull
    public static String buildLocationServicesDisabledMessage(@NonNull String actionLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append("Turn on Location in Android to ").append(actionLabel).append('.');
        if (isLikelyEmulator()) {
            builder.append(" In the Android Studio emulator, send a mock fix from Extended Controls > Location or Device Manager > Location.");
        } else {
            builder.append(" Enable the device location toggle and then try again.");
        }
        return builder.toString();
    }

    @NonNull
    public static String buildLocationUnavailableMessage(@NonNull String actionLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append("A device location is not available yet to ").append(actionLabel).append('.');
        if (isLikelyEmulator()) {
            builder.append(" Send a mock GPS fix from Android Studio or the emulator's Extended Controls, then retry.");
        } else {
            builder.append(" Wait for the phone to get a location fix, then retry.");
        }
        return builder.toString();
    }

    @NonNull
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
