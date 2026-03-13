package com.example.helios.model;

/**
 * Represents a geographical point with latitude and longitude coordinates.
 * This is a custom placeholder class for storing location data.
 */
public class GeoPoint {
    private double latitude;
    private double longitude;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public GeoPoint() {}

    /**
     * Constructs a new GeoPoint with the specified latitude and longitude.
     *
     * @param latitude  The latitude coordinate.
     * @param longitude The longitude coordinate.
     */
    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * @return The latitude coordinate.
     */
    public double getLatitude() { return latitude; }

    /**
     * @param latitude The latitude coordinate.
     */
    public void setLatitude(double latitude) { this.latitude = latitude; }

    /**
     * @return The longitude coordinate.
     */
    public double getLongitude() { return longitude; }

    /**
     * @param longitude The longitude coordinate.
     */
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
