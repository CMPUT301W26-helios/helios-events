package com.example.helios.model;

/**
 * Represents a device installation that has been explicitly granted or denied administrator access.
 *
 * <p>Role in the application: this model supports device-based administrator enablement checks
 * during profile bootstrap.
 *
 * <p>Outstanding issues: administrator privilege is tied to an installation identifier, so clearing
 * app data or moving devices changes the installation identity.
 */
public class AdminDevice {
    private String installationId; // doc id can be this too
    private boolean enabled;
    private String note;
    private long addedAtMillis;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public AdminDevice() {}

    /**
     * Constructs a new AdminDevice with the specified details.
     *
     * @param installationId Unique identifier for the device installation.
     * @param enabled        Whether this device is currently enabled as an admin device.
     * @param note           A note or description for this device.
     * @param addedAtMillis  Time when the device was added in epoch milliseconds.
     */
    public AdminDevice(String installationId, boolean enabled, String note, long addedAtMillis) {
        this.installationId = installationId;
        this.enabled = enabled;
        this.note = note;
        this.addedAtMillis = addedAtMillis;
    }

    /**
     * @return The unique identifier for the device installation.
     */
    public String getInstallationId() { return installationId; }

    /**
     * @param installationId The unique identifier for the device installation.
     */
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    /**
     * @return True if the device is enabled as an admin, false otherwise.
     */
    public boolean isEnabled() { return enabled; }

    /**
     * @param enabled Whether the device should be enabled as an admin.
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * @return A note or description for this device.
     */
    public String getNote() { return note; }

    /**
     * @param note A note or description for this device.
     */
    public void setNote(String note) { this.note = note; }

    /**
     * @return Time when the device was added in epoch milliseconds.
     */
    public long getAddedAtMillis() { return addedAtMillis; }

    /**
     * @param addedAtMillis Time when the device was added in epoch milliseconds.
     */
    public void setAddedAtMillis(long addedAtMillis) { this.addedAtMillis = addedAtMillis; }
}
