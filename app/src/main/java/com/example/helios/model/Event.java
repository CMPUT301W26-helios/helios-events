package com.example.helios.model;

import java.util.List;
/**
 * Represents an event that entrants may join through the lottery-based waiting list flow.
 *
 * <p>Role in the application: this is the main domain model for entrant browsing, organizer event
 * management, QR generation, and lottery/draw state.
 *
 * <p>Potential issues: the model does not enforce invariants such as the following:
 * positive capacity, valid date ordering, sample size limits, etc...
 * Those rules currently need to be enforced elsewhere.
 */
public class Event {
    private String eventId;

    private String title;
    private String description;

    private String locationName;
    private String address;

    // epoch millis for simplicity (Firestore also supports Timestamp, but millis is easiest)
    private long startTimeMillis;
    private long endTimeMillis;

    private long registrationOpensMillis;
    private long registrationClosesMillis;

    private int capacity;
    private int sampleSize;

    private Integer waitlistLimit; // optional limit (nullable)
    private boolean geolocationRequired;

    private String lotteryGuidelines; // shown to entrants
    private String organizerUid;

    private String posterImageId;  // optional (nullable)
    private String qrCodeValue;    // optional (nullable) - could be eventId embedded

    private List<String> interests;
  
    private boolean drawHappened; // Track if the lottery draw has occurred

    /** Creates an empty event for Firebase/Firestore deserialization. (loading from server into app) */
    public Event() {}

    /**
     * Creates a populated event.
     *
     * @param eventId event identifier
     * @param title public event title
     * @param description public event description
     * @param locationName human-readable venue name
     * @param address event address
     * @param startTimeMillis event start timestamp in epoch milliseconds
     * @param endTimeMillis event end timestamp in epoch milliseconds
     * @param registrationOpensMillis registration opening timestamp in epoch milliseconds
     * @param registrationClosesMillis registration closing timestamp in epoch milliseconds
     * @param capacity number of available participant spaces
     * @param sampleSize number of entrants to sample in the draw
     * @param waitlistLimit optional maximum waiting-list size
     * @param geolocationRequired whether joining requires device geolocation
     * @param lotteryGuidelines text shown to entrants about draw behavior
     * @param organizerUid organizer profile identifier
     * @param posterImageId optional poster/image reference
     * @param qrCodeValue optional encoded QR payload value
     * @param interests tags used for discovery and filtering
     * @param drawHappened boolean denoting whether a draw has already occurred for this event
     * TODO: update drawHappened to instead store positive integer number of draws instead, for phased invites/inviting entrants after some canceled
     */
    public Event(String eventId, String title, String description, String locationName, String address,
                 long startTimeMillis, long endTimeMillis, long registrationOpensMillis, long registrationClosesMillis,
                 int capacity, int sampleSize, Integer waitlistLimit, boolean geolocationRequired,
                 String lotteryGuidelines, String organizerUid, String posterImageId, String qrCodeValue,List<String> interests, boolean drawHappened) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.locationName = locationName;
        this.address = address;

        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.registrationOpensMillis = registrationOpensMillis;
        this.registrationClosesMillis = registrationClosesMillis;

        this.capacity = capacity;
        this.sampleSize = sampleSize;
        this.waitlistLimit = waitlistLimit;
        this.geolocationRequired = geolocationRequired;
        this.lotteryGuidelines = lotteryGuidelines;
        this.organizerUid = organizerUid;
        this.posterImageId = posterImageId;
        this.qrCodeValue = qrCodeValue;
        this.interests = interests;
        this.drawHappened = false;
    }
    // Getters and Setters:
    /** @return the unique event identifier */
    public String getEventId() { return eventId; }
    /** @param eventId unique event identifier */
    public void setEventId(String eventId) { this.eventId = eventId; }
    /** @return the public event title */
    public String getTitle() { return title; }
    /** @param title public event title */
    public void setTitle(String title) { this.title = title; }
    /** @return the public event description */
    public String getDescription() { return description; }
    /** @param description public event description */
    public void setDescription(String description) { this.description = description; }
    /** @return the venue name shown to users */
    public String getLocationName() { return locationName; }
    /** @param locationName venue name shown to users */
    public void setLocationName(String locationName) { this.locationName = locationName; }
    /** @return the event address */
    public String getAddress() { return address; }
    /** @param address event address */
    public void setAddress(String address) { this.address = address; }
    /** @return event start time in epoch milliseconds */
    public long getStartTimeMillis() { return startTimeMillis; }
    /** @param startTimeMillis event start time in epoch milliseconds */
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }
    /** @return event end time in epoch milliseconds */
    public long getEndTimeMillis() { return endTimeMillis; }
    /** @param endTimeMillis event end time in epoch milliseconds */
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }
    /** @return registration opening time in epoch milliseconds */
    public long getRegistrationOpensMillis() { return registrationOpensMillis; }
    /** @param registrationOpensMillis registration opening time in epoch milliseconds */
    public void setRegistrationOpensMillis(long registrationOpensMillis) { this.registrationOpensMillis = registrationOpensMillis; }
    /** @return registration closing time in epoch milliseconds */
    public long getRegistrationClosesMillis() { return registrationClosesMillis; }
    /** @param registrationClosesMillis registration closing time in epoch milliseconds */
    public void setRegistrationClosesMillis(long registrationClosesMillis) { this.registrationClosesMillis = registrationClosesMillis; }
    /** @return participant capacity */
    public int getCapacity() { return capacity; }
    /** @param capacity participant capacity */
    public void setCapacity(int capacity) { this.capacity = capacity; }
    /** @return requested draw sample size */
    public int getSampleSize() { return sampleSize; }
    /** @param sampleSize requested draw sample size */
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }
    /** @return optional waiting-list limit */
    public Integer getWaitlistLimit() { return waitlistLimit; }
    /** @param waitlistLimit optional waiting-list limit */
    public void setWaitlistLimit(Integer waitlistLimit) { this.waitlistLimit = waitlistLimit; }
    /** @return {@code true} if joining requires geolocation */
    public boolean isGeolocationRequired() { return geolocationRequired; }
    /** @param geolocationRequired whether joining requires geolocation */
    public void setGeolocationRequired(boolean geolocationRequired) { this.geolocationRequired = geolocationRequired; }
    /** @return entrant-facing draw and lottery guidance text */
    public String getLotteryGuidelines() { return lotteryGuidelines; }
    /** @param lotteryGuidelines entrant-facing draw and lottery guidance text */
    public void setLotteryGuidelines(String lotteryGuidelines) { this.lotteryGuidelines = lotteryGuidelines; }
    /** @return organizer profile identifier */
    public String getOrganizerUid() { return organizerUid; }
    /** @param organizerUid organizer profile identifier */
    public void setOrganizerUid(String organizerUid) { this.organizerUid = organizerUid; }
    /** @return optional poster/image identifier */
    public String getPosterImageId() { return posterImageId; }
    /** @param posterImageId optional poster/image identifier */
    public void setPosterImageId(String posterImageId) { this.posterImageId = posterImageId; }
    /** @return optional encoded QR payload */
    public String getQrCodeValue() { return qrCodeValue; }
    /** @param qrCodeValue optional encoded QR payload */
    public void setQrCodeValue(String qrCodeValue) { this.qrCodeValue = qrCodeValue; }
    /** @return interest tags associated with the event */
    public List<String> getInterests() { return interests; }
    /** @param interests interest tags associated with the event */
    public void setInterests(List<String> interests) { this.interests = interests; }
    /** @return {@code true} if the organizer has already executed the draw */
    public boolean isDrawHappened() { return drawHappened; }
    /** @param drawHappened whether the organizer has already executed the draw */
    public void setDrawHappened(boolean drawHappened) { this.drawHappened = drawHappened; }
}