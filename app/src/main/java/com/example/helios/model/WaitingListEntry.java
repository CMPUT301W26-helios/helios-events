package com.example.helios.model;

/**
 * Represents one entrant's state within a single event waiting list.
 *
 * <p>Role in the application: stores lottery workflow state for waiting, invited, accepted,
 * declined, cancelled, and not-selected entrants. Includes geolocation tracking if applicable.
 */
public class WaitingListEntry {
    private String eventId;
    private String entrantUid;
    private WaitingListStatus status;
    private long joinedAtMillis;
    private long invitedAtMillis;
    private long respondedAtMillis;
    private long cancelledAtMillis;
    private String statusReason;
    private Double joinLatitude;
    private Double joinLongitude;

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

    /**
     * @return The time the entrant was officially invited from the lottery process.
     */
    public long getInvitedAtMillis() {
        return invitedAtMillis;
    }

    /**
     * @param l The time the entrant was officially invited.
     */
    public void setInvitedAtMillis(long l) {
        this.invitedAtMillis = l;
    }

    /**
     * @return Optional text reason indicating why a status changed (e.g. system timeout).
     */
    public String getStatusReason() {
        return statusReason;
    }

    /**
     * @param statusReason Optional text reason for the status.
     */
    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    /**
     * @return Time the entrant responded to an invitation (accepted or declined).
     */
    public long getRespondedAtMillis() {
        return respondedAtMillis;
    }

    /**
     * @param respondedAtMillis Time the entrant responded.
     */
    public void setRespondedAtMillis(long respondedAtMillis) {
        this.respondedAtMillis = respondedAtMillis;
    }

    /**
     * @return Time the entrant was cancelled either by themselves or an organizer.
     */
    public long getCancelledAtMillis() {
        return cancelledAtMillis;
    }

    /**
     * @param cancelledAtMillis Time the entrant was cancelled.
     */
    public void setCancelledAtMillis(long cancelledAtMillis) {
        this.cancelledAtMillis = cancelledAtMillis;
    }

    /**
     * @return The latitude coordinate recorded from the device when joining.
     */
    public Double getJoinLatitude() {
        return joinLatitude;
    }

    /**
     * @param joinLatitude The recorded join latitude coordinate.
     */
    public void setJoinLatitude(Double joinLatitude) {
        this.joinLatitude = joinLatitude;
    }

    /**
     * @return The longitude coordinate recorded from the device when joining.
     */
    public Double getJoinLongitude() {
        return joinLongitude;
    }

    /**
     * @param joinLongitude The recorded join longitude coordinate.
     */
    public void setJoinLongitude(Double joinLongitude) {
        this.joinLongitude = joinLongitude;
    }
}
