package com.example.helios.model;
/**
 * Represents an entrant, organizer, or administrator profile stored for the current authenticated device user.
 *
 * <p>Role in the application: primary identity model used for startup bootstrap, role checks,
 * profile completion, and notification preferences.
 */
public class UserProfile {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role;
    private boolean notificationsEnabled;
    private String installationId;

    /** Creates an empty profile for Firestore deserialization. */
    public UserProfile() {
        // Required for Firestore
    }

    /**
     * Creates a populated user profile.
     *
     * @param uid unique user identifier
     * @param name display name entered by the user
     * @param email contact email entered by the user
     * @param phone optional phone number entered by the user
     * @param role current application role such as {@code user} or {@code admin}
     * @param notificationsEnabled whether notifications are enabled for the profile
     * @param installationId device installation identifier associated with the profile
     */
    public UserProfile(String uid, String name, String email, String phone,
                       String role, boolean notificationsEnabled, String installationId) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.notificationsEnabled = notificationsEnabled;
        this.installationId = installationId;
    }

    /** @return unique user identifier */
    public String getUid() { return uid; }
    /** @param uid unique user identifier */
    public void setUid(String uid) { this.uid = uid; }
    /** @return current profile name */
    public String getName() { return name; }
    /**
     * Sets the profile name.
     *
     * @param name name to store; blank values are normalized to {@code null}
     */
    public void setName(String name) { this.name = emptyToNull(name); }
    /** @return current profile email */
    public String getEmail() { return email; }
    /**
     * Sets the profile email.
     *
     * @param email email to store; blank values are normalized to {@code null}
     */
    public void setEmail(String email) { this.email = emptyToNull(email); }
    /** @return optional current profile phone number */
    public String getPhone() { return phone; }
    /**
     * Sets the profile phone number.
     *
     * @param phone phone value to store; blank values are normalized to {@code null}
     */
    public void setPhone(String phone) { this.phone = emptyToNull(phone); }
    /** @return current application role */
    public String getRole() { return role; }
    /** @param role current application role */
    public void setRole(String role) { this.role = role; }
    /** @return {@code true} if notifications are enabled */
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    /** @param notificationsEnabled whether notifications are enabled */
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    /** @return associated installation identifier */
    public String getInstallationId() { return installationId; }
    /** @param installationId associated installation identifier */
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    /** @return {@code true} when the profile role is exactly {@code admin} */
    public boolean isAdmin() { return "admin".equals(role); }

    /**
     * Determines whether the profile contains the minimum information required by the app.
     *
     * @return {@code true} if both name and email are present and non-blank
     */
    public boolean hasRequiredProfileInfo() {
        return isNonEmpty(name) && isNonEmpty(email);
    }

    /**
     * Returns a displayable name when one exists.
     *
     * @return the profile name when present, otherwise {@code null}
     */
    public String getDisplayNameOrFallback() {
        return isNonEmpty(name) ? name : null;
    }

    private boolean isNonEmpty(String v) {
        return v != null && !v.trim().isEmpty();
    }

    private String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
