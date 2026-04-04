package com.example.helios.model;

/**
 * Describes the intended audience for a notification record.
 *
 * <p>Role in the application: categorizes whether a notification is addressed to a single entrant or to
 * a group such as waiting, selected, or cancelled entrants.
 */
public enum NotificationAudience {
    /** Notification addressed to a single specific recipient. */
    WAITING,
    /** Notification addressed to entrants selected/invited for the event. */
    SELECTED,
    AUDIENCE,
    /** The user has been invited to co-organize an event. */
    CO_ORGANIZER_INVITE,
    /** The user has been invited directly to a private event. */
    PRIVATE_EVENT_INVITE,
    /** The entrant has been selected and invited to the event. */
    INVITED,
    /** The entrant has accepted the invitation. */
    ACCEPTED,
    /** The entrant has declined the invitation. */
    DECLINED,
    /** The entrant has cancelled their participation or was removed. */
    CANCELLED,
    /** The entrant was not selected during the lottery process. */
    NOT_SELECTED
}
