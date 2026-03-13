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
    /** Creates an empty admin-device record for Firestore deserialization. */
    public AdminDevice() {}

    /**
     * Creates a populated admin-device record.
     *
     * @param installationId installation identifier for the device
     * @param enabled whether admin access is enabled for the installation
     * @param note optional note describing why the device was added or disabled
     * @param addedAtMillis timestamp, in epoch milliseconds, when the record was created
     */
    public AdminDevice(String installationId, boolean enabled, String note, long addedAtMillis) {
        this.installationId = installationId;
        this.enabled = enabled;
        this.note = note;
        this.addedAtMillis = addedAtMillis;
    }

    // Getters and Setters:
    /** @return the installation identifier associated with this admin device record */
    public String getInstallationId() { return installationId; }

    /** @param installationId installation identifier associated with this admin device record */
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    /** @return {@code true} when administrator access is enabled for this installation */
    public boolean isEnabled() { return enabled; }

    /** @param enabled whether administrator access is enabled for this installation */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return optional note describing the admin-device record */
    public String getNote() { return note; }

    /** @param note optional note describing the admin-device record */
    public void setNote(String note) { this.note = note; }

    /** @return the creation timestamp for the record, in epoch milliseconds */
    public long getAddedAtMillis() { return addedAtMillis; }

    /** @param addedAtMillis creation timestamp for the record, in epoch milliseconds */
    public void setAddedAtMillis(long addedAtMillis) { this.addedAtMillis = addedAtMillis; }
}