package com.example.helios.ui.common;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class EventNavArgs {
    public static final String ARG_EVENT_ID = "arg_event_id";
    public static final String ARG_OPEN_EVENT_POSTING = "arg_open_event_posting";

    private EventNavArgs() {}

    @Nullable
    public static String getEventId(@Nullable Bundle args) {
        return args == null ? null : args.getString(ARG_EVENT_ID);
    }

    public static boolean hasEventId(@Nullable Bundle args) {
        return HeliosText.isNonEmpty(getEventId(args));
    }

    public static boolean shouldOpenEventPosting(@Nullable Bundle args) {
        return args != null && args.getBoolean(ARG_OPEN_EVENT_POSTING, false);
    }

    @NonNull
    public static Bundle forEventId(@NonNull String eventId) {
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        return args;
    }

    @NonNull
    public static Bundle forEventIdAndOpenPosting(@NonNull String eventId) {
        Bundle args = forEventId(eventId);
        args.putBoolean(ARG_OPEN_EVENT_POSTING, true);
        return args;
    }
}
