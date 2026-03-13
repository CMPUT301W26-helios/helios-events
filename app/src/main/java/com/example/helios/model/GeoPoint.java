package com.example.helios.model;

// placeholder custom geopoint class instead of using firestore GeoPoint (bad)
/**
 * Simple latitude/longitude pair used to store event-related location information.
 *
 * <p>Role in the application: placeholder geographic model for geolocation-linked event features.
 *
 * <p>Issue: this is a custom placeholder instead of Firestore's native GeoPoint and has been marked for refactoring with a todo
 */
public class GeoPoint { // TODO: eventually refactor to use Firestore GeoPoint directly!!!
    private double latitude;
    private double longitude;

    /** Creates an empty geographic point for Firestore deserialization. */
    public GeoPoint() {}

    /**
     * Creates a populated geographic point.
     *
     * @param latitude latitude in decimal degrees
     * @param longitude longitude in decimal degrees
     */
    public GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** @return latitude in decimal degrees */
    public double getLatitude() { return latitude; }
    /** @param latitude latitude in decimal degrees */
    public void setLatitude(double latitude) { this.latitude = latitude; }
    /** @return longitude in decimal degrees */
    public double getLongitude() { return longitude; }
    /** @param longitude longitude in decimal degrees */
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
