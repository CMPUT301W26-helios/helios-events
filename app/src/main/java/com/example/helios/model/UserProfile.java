package com.example.helios.model;

public class UserProfile {
    private String name;
    private String email;
    private String deviceId;

    public UserProfile() {}

    public UserProfile(String deviceId, String name, String email) {
        this.deviceId = deviceId;
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
