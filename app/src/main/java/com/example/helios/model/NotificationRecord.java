package com.example.helios.model;

/**
 * Represents a notification record in the Helios application.
 * Stores information about a notification sent to a user or a group of users.
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

    /**
     * Default constructor required for Firestore deserialization.
     */
    public NotificationRecord() {}

    /**
     * Constructs a new NotificationRecord with the specified details.
     *
     * @param notificationId Unique identifier for the notification.
     * @param eventId        Optional UID of the event related to the notification.
     * @param senderUid      UID of the user who sent the notification.
     * @param recipientUid   UID of the user who received the notification (if applicable).
     * @param audience       The target audience for the notification.
     * @param title          The title of the notification.
     * @param message        The body content of the notification.
     * @param sentAtMillis   Time when the notification was sent in epoch milliseconds.
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

    /**
     * @return The unique identifier for the notification.
     */
    public String getNotificationId() { return notificationId; }

    /**
     * @param notificationId The unique identifier for the notification.
     */
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    /**
     * @return Optional UID of the event related to the notification.
     */
    public String getEventId() { return eventId; }

    /**
     * @param eventId Optional UID of the event related to the notification.
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * @return UID of the user who sent the notification.
     */
    public String getSenderUid() { return senderUid; }

    /**
     * @param senderUid UID of the user who sent the notification.
     */
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    /**
     * @return UID of the user who received the notification (if applicable).
     */
    public String getRecipientUid() { return recipientUid; }

    /**
     * @param recipientUid UID of the user who received the notification (if applicable).
     */
    public void setRecipientUid(String recipientUid) { this.recipientUid = recipientUid; }

    /**
     * @return The target audience for the notification.
     */
    public NotificationAudience getAudience() { return audience; }

    /**
     * @param audience The target audience for the notification.
     */
    public void setAudience(NotificationAudience audience) { this.audience = audience; }

    /**
     * @return The title of the notification.
     */
    public String getTitle() { return title; }

    /**
     * @param title The title of the notification.
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * @return The body content of the notification.
     */
    public String getMessage() { return message; }

    /**
     * @param message The body content of the notification.
     */
    public void setMessage(String message) { this.message = message; }

    /**
     * @return Time when the notification was sent in epoch milliseconds.
     */
    public long getSentAtMillis() { return sentAtMillis; }

    /**
     * @param sentAtMillis Time when the notification was sent in epoch milliseconds.
     */
    public void setSentAtMillis(long sentAtMillis) { this.sentAtMillis = sentAtMillis; }
}
