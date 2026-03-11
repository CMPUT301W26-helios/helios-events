package com.example.helios.model;

public class UserProfile {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role; // role can be "user" or "admin"
    private boolean notificationsEnabled;
    private String installationId;

    public UserProfile() {
        // Required for Firestore
    }

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
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = emptyToNull(name); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = emptyToNull(email); }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = emptyToNull(phone); }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public boolean isAdmin() { return "admin".equals(role); }

    public boolean hasRequiredProfileInfo() {
        return isNonEmpty(name) && isNonEmpty(email);
    }

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