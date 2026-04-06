package com.example.helios.ui.event;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventUiFormatter {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private EventUiFormatter() {}

    @NonNull
    public static String getTitle(@NonNull Event event) {
        return nonEmptyOr(event.getTitle(), "Untitled Event");
    }

    @NonNull
    public static String getDescription(@NonNull Event event) {
        return nonEmptyOr(event.getDescription(), "No event description yet.");
    }

    @NonNull
    public static String getLocationLabel(@NonNull Event event) {
        String location = nonEmptyOrNull(event.getLocationName());
        String address = nonEmptyOrNull(event.getAddress());
        if (location != null && address != null) {
            return location + " | " + address;
        }
        if (location != null) {
            return location;
        }
        return address != null ? address : "Location to be announced";
    }

    @Nullable
    public static String getGeofenceSummary(@NonNull Event event) {
        if (!event.hasGeofence()) {
            return null;
        }
        Integer radius = event.getGeofenceRadiusMeters();
        if (radius == null || radius <= 0) {
            return null;
        }
        return "Fence radius " + radius + " m";
    }

    @NonNull
    public static String getLocationDetailLabel(@NonNull Event event) {
        String baseLocation = getLocationLabel(event);
        String geofenceSummary = getGeofenceSummary(event);
        if (geofenceSummary == null) {
            return baseLocation;
        }
        if ("Location to be announced".equals(baseLocation)) {
            return geofenceSummary;
        }
        return baseLocation + " | " + geofenceSummary;
    }

    @NonNull
    public static String getDateLabel(@NonNull Event event) {
        long startMillis = event.getStartTimeMillis();
        return startMillis > 0 ? DATE_FORMAT.format(new Date(startMillis)) : "Date TBD";
    }

    @NonNull
    public static String getStartTimeLabel(@NonNull Event event) {
        long startMillis = event.getStartTimeMillis();
        return startMillis > 0 ? TIME_FORMAT.format(new Date(startMillis)) : "TBD";
    }

    @NonNull
    public static String getEndTimeLabel(@NonNull Event event) {
        long endMillis = event.getEndTimeMillis();
        return endMillis > 0 ? TIME_FORMAT.format(new Date(endMillis)) : "TBD";
    }

    @NonNull
    public static String getScheduleLabel(@NonNull Event event) {
        long startMillis = event.getStartTimeMillis();
        if (startMillis <= 0) {
            return getDateLabel(event);
        }
        return getDateLabel(event) + " | " + getStartTimeLabel(event);
    }

    @NonNull
    public static String getTagSummary(@NonNull Event event, int maxTags) {
        List<String> tags = getDisplayTags(event);
        if (tags.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(maxTags, tags.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("  |  ");
            }
            builder.append("#").append(tags.get(i));
        }
        return builder.toString();
    }

    @NonNull
    public static List<String> getDisplayTags(@NonNull Event event) {
        Map<String, String> normalizedTags = new LinkedHashMap<>();
        List<String> interests = event.getInterests();
        if (interests == null) {
            return new ArrayList<>();
        }

        for (String rawTag : interests) {
            String cleanedTag = trimToNull(rawTag);
            if (cleanedTag == null) {
                continue;
            }
            normalizedTags.putIfAbsent(cleanedTag.toLowerCase(Locale.getDefault()), cleanedTag);
        }
        return new ArrayList<>(normalizedTags.values());
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NonNull
    private static String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    @Nullable
    private static String nonEmptyOrNull(@Nullable String value) {
        return trimToNull(value);
    }
}
