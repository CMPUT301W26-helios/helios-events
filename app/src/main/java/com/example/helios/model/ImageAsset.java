package com.example.helios.model;
/**
 * Represents an uploaded image resource such as an event poster.
 *
 * <p>Role in the application: image metadata record, mostly for admin purposes and code cleanliness,
 * which links uploaded media to organizers and optionally to specific events.
 *
 * <p>Issues: The current application UI still uses placeholder images in several places, so full image-persistence integration is incomplete.
 */
public class ImageAsset {
    private String imageId;
    private String ownerUid; // organizer who uploaded
    private String eventId; // optional link
    private String storagePath; // Firebase Storage path or URL
    private long uploadedAtMillis;
    /** Creates an empty image-asset record for Firestore deserialization. */
    public ImageAsset() {}

    /**
     * Creates a populated image-asset record.
     *
     * @param imageId unique image identifier
     * @param ownerUid organizer profile identifier that owns the image
     * @param eventId optional related event identifier
     * @param storagePath Firebase Storage path or URL for the image
     * @param uploadedAtMillis upload timestamp in epoch milliseconds
     */
    public ImageAsset(String imageId, String ownerUid, String eventId, String storagePath, long uploadedAtMillis) {
        this.imageId = imageId;
        this.ownerUid = ownerUid;
        this.eventId = eventId;
        this.storagePath = storagePath;
        this.uploadedAtMillis = uploadedAtMillis;
    }

    // Getters and Setters:
    /** @return unique image identifier */
    public String getImageId() { return imageId; }
    /** @param imageId unique image identifier */
    public void setImageId(String imageId) { this.imageId = imageId; }
    /** @return organizer identifier that owns the image */
    public String getOwnerUid() { return ownerUid; }
    /** @param ownerUid organizer identifier that owns the image */
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }
    /** @return optional related event identifier */
    public String getEventId() { return eventId; }
    /** @param eventId optional related event identifier */
    public void setEventId(String eventId) { this.eventId = eventId; }
    /** @return Firebase Storage path or URL for the image */
    public String getStoragePath() { return storagePath; }
    /** @param storagePath Firebase Storage path or URL for the image */
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    /** @return upload timestamp in epoch milliseconds */
    public long getUploadedAtMillis() { return uploadedAtMillis; }
    /** @param uploadedAtMillis upload timestamp in epoch milliseconds */
    public void setUploadedAtMillis(long uploadedAtMillis) { this.uploadedAtMillis = uploadedAtMillis; }
}