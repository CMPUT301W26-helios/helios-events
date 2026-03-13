package com.example.helios.model;

/**
 * Represents a user profile in the Helios application.
 * Stores personal information, role, and notification preferences.
 */
public class UserProfile {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role; // role can be "user" or "admin"
    private boolean notificationsEnabled;
    private String installationId;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public UserProfile() {
        // Required for Firestore
    }

    /**
     * Constructs a new UserProfile with the specified details.
     *
     * @param uid                  The unique identifier for the user.
     * @param name                 The user's name.
     * @param email                The user's email address.
     * @param phone                The user's phone number.
     * @param role                 The user's role (e.g., "user", "admin").
     * @param notificationsEnabled Whether notifications are enabled for this user.
     * @param installationId       The installation ID for push notifications.
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

    /**
     * @return The user's unique identifier.
     */
    public String getUid() { return uid; }

    /**
     * @param uid The user's unique identifier.
     */
    public void setUid(String uid) { this.uid = uid; }

    /**
     * @return The user's name.
     */
    public String getName() { return name; }

    /**
     * Sets the user's name. Empty strings are converted to null.
     * @param name The user's name.
     */
    public void setName(String name) { this.name = emptyToNull(name); }

    /**
     * @return The user's email address.
     */
    public String getEmail() { return email; }

    /**
     * Sets the user's email address. Empty strings are converted to null.
     * @param email The user's email address.
     */
    public void setEmail(String email) { this.email = emptyToNull(email); }

    /**
     * @return The user's phone number.
     */
    public String getPhone() { return phone; }

    /**
     * Sets the user's phone number. Empty strings are converted to null.
     * @param phone The user's phone number.
     */
    public void setPhone(String phone) { this.phone = emptyToNull(phone); }

    /**
     * @return The user's role.
     */
    public String getRole() { return role; }

    /**
     * @param role The user's role.
     */
    public void setRole(String role) { this.role = role; }

    /**
     * @return True if notifications are enabled, false otherwise.
     */
    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    /**
     * @param notificationsEnabled Whether notifications should be enabled.
     */
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    /**
     * @return The installation ID for notifications.
     */
    public String getInstallationId() { return installationId; }

    /**
     * @param installationId The installation ID for notifications.
     */
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    /**
     * Checks if the user has administrative privileges.
     * @return True if the user is an admin, false otherwise.
     */
    public boolean isAdmin() { return "admin".equals(role); }

    /**
     * Checks if the user has provided the required profile information (name and email).
     * @return True if required info is present, false otherwise.
     */
    public boolean hasRequiredProfileInfo() {
        return isNonEmpty(name) && isNonEmpty(email);
    }

    /**
     * Returns the name if it is not empty, otherwise returns null.
     * @return The display name or null.
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
