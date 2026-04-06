package com.example.helios.model;

/**
 * Metadata record for an uploaded image resource such as an event poster.
 *
 * <p>Links uploaded media to its organizer owner and optionally to a specific event.
 * Used by admin moderation and image-cleanup flows.
 */
public class ImageAsset {
    private String imageId;
    private String ownerUid; // organizer who uploaded
    private String eventId; // optional link
    private String storagePath; // Firebase Storage path or URL
    private long uploadedAtMillis;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public ImageAsset() {}

    /**
     * Constructs a new ImageAsset with the specified details.
     *
     * @param imageId          Unique identifier for the image.
     * @param ownerUid         UID of the user who uploaded the image.
     * @param eventId          Optional UID of the event associated with this image.
     * @param storagePath      Path or URL where the image is stored in Firebase Storage.
     * @param uploadedAtMillis Time when the image was uploaded in epoch milliseconds.
     */
    public ImageAsset(String imageId, String ownerUid, String eventId, String storagePath, long uploadedAtMillis) {
        this.imageId = imageId;
        this.ownerUid = ownerUid;
        this.eventId = eventId;
        this.storagePath = storagePath;
        this.uploadedAtMillis = uploadedAtMillis;
    }

    /**
     * @return The unique identifier for the image.
     */
    public String getImageId() { return imageId; }

    /**
     * @param imageId The unique identifier for the image.
     */
    public void setImageId(String imageId) { this.imageId = imageId; }

    /**
     * @return UID of the user who uploaded the image.
     */
    public String getOwnerUid() { return ownerUid; }

    /**
     * @param ownerUid UID of the user who uploaded the image.
     */
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    /**
     * @return Optional UID of the event associated with this image.
     */
    public String getEventId() { return eventId; }

    /**
     * @param eventId Optional UID of the event associated with this image.
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * @return Path or URL where the image is stored.
     */
    public String getStoragePath() { return storagePath; }

    /**
     * @param storagePath Path or URL where the image is stored.
     */
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    /**
     * @return Time when the image was uploaded in epoch milliseconds.
     */
    public long getUploadedAtMillis() { return uploadedAtMillis; }

    /**
     * @param uploadedAtMillis Time when the image was uploaded in epoch milliseconds.
     */
    public void setUploadedAtMillis(long uploadedAtMillis) { this.uploadedAtMillis = uploadedAtMillis; }
}
