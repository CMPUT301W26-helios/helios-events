package com.example.helios.model;

public class WaitingListEntry {
    private String eventId;
    private String entrantUid;

    private WaitingListStatus status;

    private long joinedAtMillis;
    // nullables:
    private GeoPoint joinedLocation;

    private Long invitedAtMillis;
    private Long respondedAtMillis;
    private Long cancelledAtMillis;
    // END nullables

    private String note; // optional reason/note

    public WaitingListEntry() {}

    public WaitingListEntry(String eventId, String entrantUid, WaitingListStatus status,
                            long joinedAtMillis, GeoPoint joinedLocation,
                            Long invitedAtMillis, Long respondedAtMillis, Long cancelledAtMillis,
                            String note) {
        this.eventId = eventId;
        this.entrantUid = entrantUid;
        this.status = status;
        this.joinedAtMillis = joinedAtMillis;
        this.joinedLocation = joinedLocation;
        this.invitedAtMillis = invitedAtMillis;
        this.respondedAtMillis = respondedAtMillis;
        this.cancelledAtMillis = cancelledAtMillis;
        this.note = note;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEntrantUid() { return entrantUid; }
    public void setEntrantUid(String entrantUid) { this.entrantUid = entrantUid; }

    public WaitingListStatus getStatus() { return status; }
    public void setStatus(WaitingListStatus status) { this.status = status; }

    public long getJoinedAtMillis() { return joinedAtMillis; }
    public void setJoinedAtMillis(long joinedAtMillis) { this.joinedAtMillis = joinedAtMillis; }

    public GeoPoint getJoinedLocation() { return joinedLocation; }
    public void setJoinedLocation(GeoPoint joinedLocation) { this.joinedLocation = joinedLocation; }

    public Long getInvitedAtMillis() { return invitedAtMillis; }
    public void setInvitedAtMillis(Long invitedAtMillis) { this.invitedAtMillis = invitedAtMillis; }

    public Long getRespondedAtMillis() { return respondedAtMillis; }
    public void setRespondedAtMillis(Long respondedAtMillis) { this.respondedAtMillis = respondedAtMillis; }

    public Long getCancelledAtMillis() { return cancelledAtMillis; }
    public void setCancelledAtMillis(Long cancelledAtMillis) { this.cancelledAtMillis = cancelledAtMillis; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}