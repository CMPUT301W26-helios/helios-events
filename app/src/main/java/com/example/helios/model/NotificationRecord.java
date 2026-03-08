package com.example.helios.model;

public class NotificationRecord {
    private String notificationId;

    private String eventId; // nullable?
    private String senderUid; // organizer/admin
    private String recipientUid; // nullable if group audience

    private NotificationAudience audience;

    private String title;
    private String message;

    private long sentAtMillis;

    public NotificationRecord() {}

    public NotificationRecord(String notificationId, String eventId, String senderUid, String recipientUid,
                              NotificationAudience audience, String title, String message, long sentAtMillis) {
        this.notificationId = notificationId;
        this.eventId = eventId;
        this.senderUid = senderUid;
        this.recipientUid = recipientUid;
        this.audience = audience;
        this.title = title;
        this.message = message;
        this.sentAtMillis = sentAtMillis;
    }

    // Getters and Setters:
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getRecipientUid() { return recipientUid; }
    public void setRecipientUid(String recipientUid) { this.recipientUid = recipientUid; }

    public NotificationAudience getAudience() { return audience; }
    public void setAudience(NotificationAudience audience) { this.audience = audience; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getSentAtMillis() { return sentAtMillis; }
    public void setSentAtMillis(long sentAtMillis) { this.sentAtMillis = sentAtMillis; }
}