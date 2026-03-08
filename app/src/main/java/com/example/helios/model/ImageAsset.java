package com.example.helios.model;

public class ImageAsset {
    private String imageId;
    private String ownerUid; // organizer who uploaded
    private String eventId; // optional link
    private String storagePath; // Firebase Storage path or URL
    private long uploadedAtMillis;

    public ImageAsset() {}

    public ImageAsset(String imageId, String ownerUid, String eventId, String storagePath, long uploadedAtMillis) {
        this.imageId = imageId;
        this.ownerUid = ownerUid;
        this.eventId = eventId;
        this.storagePath = storagePath;
        this.uploadedAtMillis = uploadedAtMillis;
    }

    // Getters and Setters:
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public long getUploadedAtMillis() { return uploadedAtMillis; }
    public void setUploadedAtMillis(long uploadedAtMillis) { this.uploadedAtMillis = uploadedAtMillis; }
}