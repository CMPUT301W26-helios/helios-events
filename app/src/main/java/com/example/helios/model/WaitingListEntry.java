package com.example.helios.model;

public class WaitingListEntry {
    private String eventId;
    private String entrantUid;
    private WaitingListStatus status;
    private long joinedAtMillis;

    public WaitingListEntry() {}

    public WaitingListEntry(String eventId, String entrantUid, WaitingListStatus status, long joinedAtMillis) {
        this.eventId = eventId;
        this.entrantUid = entrantUid;
        this.status = status;
        this.joinedAtMillis = joinedAtMillis;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEntrantUid() { return entrantUid; }
    public void setEntrantUid(String entrantUid) { this.entrantUid = entrantUid; }

    public WaitingListStatus getStatus() { return status; }
    public void setStatus(WaitingListStatus status) { this.status = status; }

    public long getJoinedAtMillis() { return joinedAtMillis; }
    public void setJoinedAtMillis(long joinedAtMillis) { this.joinedAtMillis = joinedAtMillis; }
}