package com.example.helios.model;

/**
 * Enumerates the supported states for an entrant in the event waiting list flow.
 */
public enum WaitingListStatus {
    /** Entrant has joined the waiting list and has not yet been drawn. */
    WAITING,
    /** Entrant has been invited to register/confirm a spot. */
    INVITED,
    /** Entrant has accepted an invitation and confirmed participation. */
    ACCEPTED,
    /** Entrant declined an invitation. */
    DECLINED,
    /** Entrant left the waiting list or was cancelled. */
    CANCELLED,
    /** Entrant was not selected during a draw. */
    NOT_SELECTED
}
