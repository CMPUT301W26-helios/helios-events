package com.example.helios.model;

/**
 * Enumeration representing the target audience for a notification.
 */
public enum NotificationAudience {
    /** Notification intended for a single individual. */
    INDIVIDUAL,
    /** Notification intended for everyone on the waiting list. */
    WAITING,
    /** Notification intended for those who have been selected (invited or accepted). */
    SELECTED,   // invited/accepted
    /** Notification intended for those whose participation was cancelled. */
    CANCELLED
}
