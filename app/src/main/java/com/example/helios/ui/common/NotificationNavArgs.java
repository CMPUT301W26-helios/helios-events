package com.example.helios.ui.common;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class NotificationNavArgs {
    public static final String EXTRA_OPENED_FROM_NOTIFICATION =
            "com.example.helios.extra.OPENED_FROM_NOTIFICATION";
    public static final String EXTRA_NOTIFICATION_EVENT_ID =
            "com.example.helios.extra.NOTIFICATION_EVENT_ID";
    public static final String REQUEST_OPEN_EVENT =
            "com.example.helios.request.OPEN_NOTIFICATION_EVENT";

    private NotificationNavArgs() {}

    public static void markNotificationIntent(
            @NonNull Intent intent,
            @Nullable String eventId
    ) {
        intent.putExtra(EXTRA_OPENED_FROM_NOTIFICATION, true);
        if (HeliosText.isNonEmpty(eventId)) {
            intent.putExtra(EXTRA_NOTIFICATION_EVENT_ID, eventId);
            return;
        }
        intent.removeExtra(EXTRA_NOTIFICATION_EVENT_ID);
    }

    public static boolean isNotificationIntent(@Nullable Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false);
    }

    @Nullable
    public static String getEventId(@Nullable Intent intent) {
        return intent == null ? null : intent.getStringExtra(EXTRA_NOTIFICATION_EVENT_ID);
    }

    public static boolean hasEventId(@Nullable Intent intent) {
        return HeliosText.isNonEmpty(getEventId(intent));
    }

    public static void clear(@NonNull Intent intent) {
        intent.removeExtra(EXTRA_OPENED_FROM_NOTIFICATION);
        intent.removeExtra(EXTRA_NOTIFICATION_EVENT_ID);
    }
}
