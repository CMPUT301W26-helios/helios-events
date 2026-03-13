package com.example.helios.model;
/**
 * Represents one entrant's state within a single event waiting list.
 *
 * <p>Role in the application: stores lottery workflow state for waiting, invited, accepted,
 * declined, cancelled, and not-selected entrants.
 *
 * <p>Issues: allowed state transitions are not enforced in this model and are currently
 * handled elsewhere in services or UI code.
 */
public class WaitingListEntry {
    private String eventId;
    private String entrantUid;
    private WaitingListStatus status;
    private long joinedAtMillis;

    /** Creates an empty waiting-list entry for Firestore deserialization. */
    public WaitingListEntry() {}

    /**
     * Creates a populated waiting-list entry.
     *
     * @param eventId associated event identifier
     * @param entrantUid entrant profile identifier
     * @param status current waiting-list status
     * @param joinedAtMillis join timestamp in epoch milliseconds
     */
    public WaitingListEntry(String eventId, String entrantUid, WaitingListStatus status, long joinedAtMillis) {
        this.eventId = eventId;
        this.entrantUid = entrantUid;
        this.status = status;
        this.joinedAtMillis = joinedAtMillis;
    }

    /** @return associated event identifier */
    public String getEventId() { return eventId; }
    /** @param eventId associated event identifier */
    public void setEventId(String eventId) { this.eventId = eventId; }
    /** @return entrant profile identifier */
    public String getEntrantUid() { return entrantUid; }
    /** @param entrantUid entrant profile identifier */
    public void setEntrantUid(String entrantUid) { this.entrantUid = entrantUid; }
    /** @return current waiting-list status */
    public WaitingListStatus getStatus() { return status; }
    /** @param status current waiting-list status */
    public void setStatus(WaitingListStatus status) { this.status = status; }
    /** @return join timestamp in epoch milliseconds */
    public long getJoinedAtMillis() { return joinedAtMillis; }
    /** @param joinedAtMillis join timestamp in epoch milliseconds */
    public void setJoinedAtMillis(long joinedAtMillis) { this.joinedAtMillis = joinedAtMillis; }
}
