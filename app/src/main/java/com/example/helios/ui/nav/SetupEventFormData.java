package com.example.helios.ui.nav;

import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.Event;
import com.example.helios.ui.common.HeliosText;

import java.util.ArrayList;
import java.util.List;

final class SetupEventFormData {
    private final String title;
    private final String description;
    private final String lotteryGuidelines;
    private final String locationNameRaw;
    private final String addressRaw;
    private final String tagsRaw;
    private final String geofenceLatitudeRaw;
    private final String geofenceLongitudeRaw;
    private final String geofenceRadiusRaw;
    private final String maxEntrantsRaw;
    private final String waitlistLimitRaw;

    private SetupEventFormData(
            @NonNull String title,
            @NonNull String description,
            @NonNull String lotteryGuidelines,
            @NonNull String locationNameRaw,
            @NonNull String addressRaw,
            @NonNull String tagsRaw,
            @NonNull String geofenceLatitudeRaw,
            @NonNull String geofenceLongitudeRaw,
            @NonNull String geofenceRadiusRaw,
            @NonNull String maxEntrantsRaw,
            @NonNull String waitlistLimitRaw
    ) {
        this.title = title;
        this.description = description;
        this.lotteryGuidelines = lotteryGuidelines;
        this.locationNameRaw = locationNameRaw;
        this.addressRaw = addressRaw;
        this.tagsRaw = tagsRaw;
        this.geofenceLatitudeRaw = geofenceLatitudeRaw;
        this.geofenceLongitudeRaw = geofenceLongitudeRaw;
        this.geofenceRadiusRaw = geofenceRadiusRaw;
        this.maxEntrantsRaw = maxEntrantsRaw;
        this.waitlistLimitRaw = waitlistLimitRaw;
    }

    @NonNull
    static SetupEventFormData from(
            @NonNull EditText nameInput,
            @NonNull EditText descriptionInput,
            @NonNull EditText lotteryGuidelinesInput,
            @NonNull EditText locationNameInput,
            @NonNull EditText addressInput,
            @NonNull EditText tagsInput,
            @NonNull EditText geofenceLatitudeInput,
            @NonNull EditText geofenceLongitudeInput,
            @NonNull EditText geofenceRadiusInput,
            @NonNull EditText maxEntrantsInput,
            @NonNull EditText waitlistLimitInput
    ) {
        return new SetupEventFormData(
                safeText(nameInput),
                safeText(descriptionInput),
                safeText(lotteryGuidelinesInput),
                safeText(locationNameInput),
                safeText(addressInput),
                safeText(tagsInput),
                safeText(geofenceLatitudeInput),
                safeText(geofenceLongitudeInput),
                safeText(geofenceRadiusInput),
                safeText(maxEntrantsInput),
                safeText(waitlistLimitInput)
        );
    }

    @Nullable
    String validate(
            long registrationOpensMillis,
            long registrationClosesMillis,
            boolean geolocationRequired
    ) {
        if (TextUtils.isEmpty(title)) {
            return "Event name is required.";
        }
        if (registrationClosesMillis < registrationOpensMillis) {
            return "Registration end date must be after start date.";
        }
        Integer capacity = parseOptionalPositiveInt(maxEntrantsRaw);
        if (!TextUtils.isEmpty(maxEntrantsRaw) && capacity == null) {
            return "Maximum entrants must be a whole number greater than 0.";
        }
        Integer waitlistLimit = parseOptionalPositiveInt(waitlistLimitRaw);
        if (!TextUtils.isEmpty(waitlistLimitRaw) && waitlistLimit == null) {
            return "Waitlist limit must be a whole number greater than 0.";
        }
        Double geofenceLatitude = parseOptionalDouble(geofenceLatitudeRaw);
        if (!TextUtils.isEmpty(geofenceLatitudeRaw) && geofenceLatitude == null) {
            return "Geofence latitude must be a valid decimal value.";
        }
        if (geofenceLatitude != null && (geofenceLatitude < -90d || geofenceLatitude > 90d)) {
            return "Geofence latitude must be between -90 and 90.";
        }
        Double geofenceLongitude = parseOptionalDouble(geofenceLongitudeRaw);
        if (!TextUtils.isEmpty(geofenceLongitudeRaw) && geofenceLongitude == null) {
            return "Geofence longitude must be a valid decimal value.";
        }
        if (geofenceLongitude != null && (geofenceLongitude < -180d || geofenceLongitude > 180d)) {
            return "Geofence longitude must be between -180 and 180.";
        }
        Integer geofenceRadius = parseOptionalPositiveInt(geofenceRadiusRaw);
        if (!TextUtils.isEmpty(geofenceRadiusRaw) && geofenceRadius == null) {
            return "Geofence radius must be a whole number greater than 0.";
        }
        boolean hasAnyGeofenceInput = geofenceLatitude != null
                || geofenceLongitude != null
                || geofenceRadius != null;
        boolean hasCompleteGeofence = geofenceLatitude != null
                && geofenceLongitude != null
                && geofenceRadius != null;
        if (hasAnyGeofenceInput && !hasCompleteGeofence) {
            return "Enter latitude, longitude, and fence radius to save the geofence.";
        }
        if (geolocationRequired && !hasCompleteGeofence) {
            return "Geolocation-required events need a mapped geofence center and radius.";
        }
        return null;
    }

    int resolveCapacity(int fallbackCapacity) {
        Integer parsed = parseOptionalPositiveInt(maxEntrantsRaw);
        return parsed == null ? fallbackCapacity : parsed;
    }

    @Nullable
    Integer resolveWaitlistLimit() {
        return parseOptionalPositiveInt(waitlistLimitRaw);
    }

    @Nullable
    Double resolveGeofenceLatitude() {
        return parseOptionalDouble(geofenceLatitudeRaw);
    }

    @Nullable
    Double resolveGeofenceLongitude() {
        return parseOptionalDouble(geofenceLongitudeRaw);
    }

    @Nullable
    Integer resolveGeofenceRadiusMeters() {
        return parseOptionalPositiveInt(geofenceRadiusRaw);
    }

    @Nullable
    List<String> parseInterests() {
        if (tagsRaw.isEmpty()) {
            return null;
        }

        List<String> interests = new ArrayList<>();
        for (String part : tagsRaw.split(",")) {
            String trimmed = HeliosText.trimToNull(part);
            if (trimmed != null) {
                interests.add(trimmed);
            }
        }
        return interests.isEmpty() ? null : interests;
    }

    void applyTo(
            @NonNull Event event,
            int capacity,
            long registrationOpensMillis,
            long registrationClosesMillis,
            boolean geolocationRequired,
            boolean privateEvent
    ) {
        event.setTitle(title);
        event.setDescription(description);
        event.setLocationName(HeliosText.trimToNull(locationNameRaw));
        event.setAddress(HeliosText.trimToNull(addressRaw));
        event.setCapacity(capacity);
        event.setSampleSize(capacity);
        event.setRegistrationOpensMillis(registrationOpensMillis);
        event.setRegistrationClosesMillis(registrationClosesMillis);
        event.setGeolocationRequired(geolocationRequired);
        event.setPrivateEvent(privateEvent);
        event.setWaitlistLimit(resolveWaitlistLimit());
        event.setLotteryGuidelines(lotteryGuidelines);
        event.setInterests(parseInterests());
        Double geofenceLatitude = resolveGeofenceLatitude();
        Double geofenceLongitude = resolveGeofenceLongitude();
        Integer geofenceRadiusMeters = resolveGeofenceRadiusMeters();
        if (geofenceLatitude != null && geofenceLongitude != null && geofenceRadiusMeters != null) {
            event.setGeofenceCenter(geofenceLatitude, geofenceLongitude);
            event.setGeofenceRadiusMeters(geofenceRadiusMeters);
        } else {
            event.setGeofenceCenter((com.example.helios.model.GeoPoint) null);
            event.setGeofenceRadiusMeters(null);
        }
        if (privateEvent) {
            event.setQrCodeValue(null);
        }
    }

    @NonNull
    String getTitle() {
        return title;
    }

    @NonNull
    String getDescription() {
        return description;
    }

    @Nullable
    String getLocationName() {
        return HeliosText.trimToNull(locationNameRaw);
    }

    @Nullable
    String getAddress() {
        return HeliosText.trimToNull(addressRaw);
    }

    @NonNull
    String getLotteryGuidelines() {
        return lotteryGuidelines;
    }

    @Nullable
    List<String> getInterests() {
        return parseInterests();
    }

    @Nullable
    private Integer parseOptionalPositiveInt(@NonNull String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(rawValue);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Double parseOptionalDouble(@NonNull String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            return null;
        }
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String safeText(@NonNull EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
