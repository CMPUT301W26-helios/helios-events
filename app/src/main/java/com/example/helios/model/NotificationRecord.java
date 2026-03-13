package com.example.helios.model;

/**
 * Represents a notification sent by an organizer or administrator.
 *
 * <p>Role in the application: stores notification metadata for audit/history features and audience-based
 * delivery behavior.
 *
 * <p>Issues: nullability expectations for {@code eventId}, {@code senderUid} and {@code recipientUid} are only
 * implicit, they should instead be documented consistently across the app.
 */
public class NotificationRecord {
    private String notificationId;
    private String eventId;
    private String senderUid;
    private String recipientUid;
    private NotificationAudience audience;
    private String title;
    private String message;
    private long sentAtMillis;

    /** Creates an empty notification record for Firestore deserialization. */
    public NotificationRecord() {}

    /**
     * Creates a populated notification record.
     *
     * @param notificationId unique notification identifier
     * @param eventId optional related event identifier
     * @param senderUid organizer/admin sender identifier
     * @param recipientUid optional recipient identifier for individual notifications
     * @param audience target notification audience
     * @param title short notification title
     * @param message notification body text
     * @param sentAtMillis sent timestamp in epoch milliseconds
     */
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

    /** @return unique notification identifier */
    public String getNotificationId() { return notificationId; }
    /** @param notificationId unique notification identifier */
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    /** @return optional related event identifier */
    public String getEventId() { return eventId; }
    /** @param eventId optional related event identifier */
    public void setEventId(String eventId) { this.eventId = eventId; }
    /** @return organizer/admin sender identifier */
    public String getSenderUid() { return senderUid; }
    /** @param senderUid organizer/admin sender identifier */
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }
    /** @return optional recipient identifier for individual notifications */
    public String getRecipientUid() { return recipientUid; }
    /** @param recipientUid optional recipient identifier for individual notifications */
    public void setRecipientUid(String recipientUid) { this.recipientUid = recipientUid; }
    /** @return target notification audience */
    public NotificationAudience getAudience() { return audience; }
    /** @param audience target notification audience */
    public void setAudience(NotificationAudience audience) { this.audience = audience; }
    /** @return short notification title */
    public String getTitle() { return title; }
    /** @param title short notification title */
    public void setTitle(String title) { this.title = title; }
    /** @return notification body text */
    public String getMessage() { return message; }
    /** @param message notification body text */
    public void setMessage(String message) { this.message = message; }
    /** @return sent timestamp in epoch milliseconds */
    public long getSentAtMillis() { return sentAtMillis; }
    /** @param sentAtMillis sent timestamp in epoch milliseconds */
    public void setSentAtMillis(long sentAtMillis) { this.sentAtMillis = sentAtMillis; }
}
