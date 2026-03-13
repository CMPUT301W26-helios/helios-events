package com.example.helios.model;

/**
 * Enumeration representing the possible statuses of an entrant in a waiting list.
 */
public enum WaitingListStatus {
    /** The entrant is currently waiting to be selected. */
    WAITING,
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
