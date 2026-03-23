package com.example.helios.model;
/**
 * Represents one entrant's state within a single event waiting list.
 *
 * <p>Role in the application: stores lottery workflow state for waiting, invited, accepted,
 * declined, cancelled, and not-selected entrants.
 *
 * <p>Issues: allowed state transitions are not enforced in this model and are currently
 * handled elsewhere in services or UI code.
 * Alt Description:
 * Represents an entry in the waiting list for a specific event.
 * Tracks the entrant's UID, their status, and when they joined the list.
 */
public class WaitingListEntry {
    private String eventId;
    private String entrantUid;
    private WaitingListStatus status;
    private long joinedAtMillis;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public WaitingListEntry() {}

    /**
     * Constructs a new WaitingListEntry with the specified details.
     *
     * @param eventId        The unique identifier for the event.
     * @param entrantUid     The unique identifier for the entrant.
     * @param status         The current status of the entrant on the waiting list.
     * @param joinedAtMillis The time the entrant joined the waiting list in epoch milliseconds.
     */
    public WaitingListEntry(String eventId, String entrantUid, WaitingListStatus status, long joinedAtMillis) {
        this.eventId = eventId;
        this.entrantUid = entrantUid;
        this.status = status;
        this.joinedAtMillis = joinedAtMillis;
    }

    /**
     * @return The unique identifier for the event.
     */
    public String getEventId() { return eventId; }

    /**
     * @param eventId The unique identifier for the event.
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * @return The unique identifier for the entrant.
     */
    public String getEntrantUid() { return entrantUid; }

    /**
     * @param entrantUid The unique identifier for the entrant.
     */
    public void setEntrantUid(String entrantUid) { this.entrantUid = entrantUid; }

    /**
     * @return The current status of the entrant on the waiting list.
     */
    public WaitingListStatus getStatus() { return status; }

    /**
     * @param status The current status of the entrant on the waiting list.
     */
    public void setStatus(WaitingListStatus status) { this.status = status; }

    /**
     * @return The time the entrant joined the waiting list in epoch milliseconds.
     */
    public long getJoinedAtMillis() { return joinedAtMillis; }

    /**
     * @param joinedAtMillis The time the entrant joined the waiting list in epoch milliseconds.
     */
    public void setJoinedAtMillis(long joinedAtMillis) { this.joinedAtMillis = joinedAtMillis; }

    public void setInvitedAtMillis(long l) {
    }

    public void setRespondedAtMillis(long l) {
    }
}
