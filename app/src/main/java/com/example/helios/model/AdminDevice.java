package com.example.helios.model;

public class AdminDevice {
    private String installationId; // doc id can be this too
    private boolean enabled;
    private String note;
    private long addedAtMillis;

    public AdminDevice() {}

    public AdminDevice(String installationId, boolean enabled, String note, long addedAtMillis) {
        this.installationId = installationId;
        this.enabled = enabled;
        this.note = note;
        this.addedAtMillis = addedAtMillis;
    }

    // Getters and Setters:
    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getAddedAtMillis() { return addedAtMillis; }
    public void setAddedAtMillis(long addedAtMillis) { this.addedAtMillis = addedAtMillis; }
}