package com.example.helios.model;

// placeholder custom geopoint class instead of using firestore GeoPoint (bad)
public class GeoPoint { // TODO: eventually refactor to use Firestore GeoPoint directly!!!
    private double latitude;
    private double longitude;

    public GeoPoint() {}

    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters:
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}