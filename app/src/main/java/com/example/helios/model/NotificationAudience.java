package com.example.helios.model;

/**
 * Describes the intended audience for a notification record.
 *
 * <p>Role in the application: categorizes whether a notification is addressed to a single entrant or to
 * a group such as waiting, selected, or cancelled entrants.
 */
public enum NotificationAudience {
    /** Notification addressed to a single specific recipient. */
    INDIVIDUAL,
    /** Notification addressed to entrants currently on the waiting list. */
    WAITING,
    /** Notification addressed to invited or accepted entrants. */
    SELECTED,
    /** Notification addressed to cancelled or declined entrants. */
    CANCELLED
}
