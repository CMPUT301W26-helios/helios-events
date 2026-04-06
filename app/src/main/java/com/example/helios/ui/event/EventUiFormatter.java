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
    private static final SimpleDateFormat FULL_DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());
    private static final SimpleDateFormat REGISTRATION_TIME_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat SHORT_TIME_FORMAT =
            new SimpleDateFormat("h:mm", Locale.getDefault());
    private static final SimpleDateFormat MERIDIEM_FORMAT =
            new SimpleDateFormat("a", Locale.getDefault());

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
    public static String getRegistrationStartDateLabel(@NonNull Event event) {
        long opensMillis = event.getRegistrationOpensMillis();
        return opensMillis > 0 ? FULL_DATE_FORMAT.format(new Date(opensMillis)) : "TBD";
    }

    @NonNull
    public static String getRegistrationEndDateLabel(@NonNull Event event) {
        long closesMillis = event.getRegistrationClosesMillis();
        return closesMillis > 0 ? FULL_DATE_FORMAT.format(new Date(closesMillis)) : "TBD";
    }

    @NonNull
    public static String getRegistrationEndTimeLabel(@NonNull Event event) {
        long closesMillis = event.getRegistrationClosesMillis();
        return closesMillis > 0 ? TIME_FORMAT.format(new Date(closesMillis)) : "TBD";
    }

    @NonNull
    public static String getRegistrationStatusLabel(@NonNull Event event, long nowMillis) {
        long opensMillis = event.getRegistrationOpensMillis();
        long closesMillis = event.getRegistrationClosesMillis();
        if (opensMillis <= 0 || closesMillis <= 0) {
            return "Registration details unavailable";
        }
        if (nowMillis < opensMillis) {
            return "Registration opens on "
                    + FULL_DATE_FORMAT.format(new Date(opensMillis))
                    + " @ "
                    + REGISTRATION_TIME_FORMAT.format(new Date(opensMillis));
        }
        if (nowMillis < closesMillis) {
            return "Registration closes at "
                    + FULL_DATE_FORMAT.format(new Date(closesMillis))
                    + " @ "
                    + REGISTRATION_TIME_FORMAT.format(new Date(closesMillis));
        }
        return "Registration closed";
    }

    @NonNull
    public static String getTimeRangeLabel(@NonNull Event event) {
        long startMillis = event.getStartTimeMillis();
        long endMillis = event.getEndTimeMillis();
        if (startMillis <= 0) {
            return "Time TBD";
        }
        if (endMillis <= startMillis) {
            return getStartTimeLabel(event);
        }

        Date startDate = new Date(startMillis);
        Date endDate = new Date(endMillis);
        String startMeridiem = MERIDIEM_FORMAT.format(startDate);
        String endMeridiem = MERIDIEM_FORMAT.format(endDate);
        if (startMeridiem.equals(endMeridiem)) {
            return SHORT_TIME_FORMAT.format(startDate) + " - " + TIME_FORMAT.format(endDate);
        }
        return TIME_FORMAT.format(startDate) + " - " + TIME_FORMAT.format(endDate);
    }

    @NonNull
    public static String getScheduleLabel(@NonNull Event event) {
        long startMillis = event.getStartTimeMillis();
        if (startMillis <= 0) {
            return getDateLabel(event);
        }
        return getDateLabel(event) + " | " + getTimeRangeLabel(event);
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

    /**
     * Returns a short capacity chip label for event cards.
     * Shows "X/Y" where X is the lottery sample size and Y is the total waiting list capacity,
     * or just "Y seats" when sample size equals capacity or is not set.
     */
    @NonNull
    public static String getCapacityChipLabel(@NonNull Event event) {
        int capacity = event.getCapacity();
        int sampleSize = event.getSampleSize();
        if (sampleSize > 0 && sampleSize < capacity) {
            return sampleSize + "/" + capacity;
        }
        return capacity + " seats";
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
