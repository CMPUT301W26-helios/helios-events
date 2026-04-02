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
 *
 * Alt Description:
 * Represents an event in the Helios application.
 * Contains details about the event, its location, timing, registration, and lottery settings.
 * TODO! Update and consolidate these descriptions!
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
    private boolean privateEvent;
    private List<String> coOrganizerUids;
    private List<String> pendingCoOrganizerUids;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public Event() {}

    /**
     * Constructs a new Event with the specified details.
     *
     * @param eventId                  Unique identifier for the event.
     * @param title                    Title of the event.
     * @param description              Detailed description of the event.
     * @param locationName             Name of the location.
     * @param address                  Physical address of the event.
     * @param startTimeMillis          Start time in epoch milliseconds.
     * @param endTimeMillis            End time in epoch milliseconds.
     * @param registrationOpensMillis  Time when registration starts.
     * @param registrationClosesMillis Time when registration ends.
     * @param capacity                 Maximum number of attendees.
     * @param sampleSize               Number of people to be selected in the lottery.
     * @param waitlistLimit            Optional limit for the waiting list.
     * @param geolocationRequired      Whether geolocation is required for entrants.
     * @param lotteryGuidelines        Guidelines for the lottery process.
     * @param organizerUid             UID of the user who organized the event.
     * @param posterImageId            ID of the poster image (optional).
     * @param qrCodeValue              Value encoded in the QR code (optional).
     * @param interests                List of interests associated with the event.
     * @param drawHappened             Boolean denoting whether a draw has already occurred for this event
     * TODO: update drawHappened to instead store positive integer number of draws instead, for phased invites/inviting entrants after some canceled
     */
    public Event(String eventId, String title, String description, String locationName, String address,
                 long startTimeMillis, long endTimeMillis, long registrationOpensMillis, long registrationClosesMillis,
                 int capacity, int sampleSize, Integer waitlistLimit, boolean geolocationRequired,
                 String lotteryGuidelines, String organizerUid, String posterImageId, String qrCodeValue,List<String> interests, boolean drawHappened, boolean privateEvent) {
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
        this.privateEvent = privateEvent;
    }

    /**
     * @return The unique identifier for the event.
     */
    public String getEventId() { return eventId; }

    /**
     * @param eventId The unique identifier for the event.
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * @return The title of the event.
     */
    public String getTitle() { return title; }

    /**
     * @param title The title of the event.
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * @return The detailed description of the event.
     */
    public String getDescription() { return description; }

    /**
     * @param description The detailed description of the event.
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * @return The name of the location.
     */
    public String getLocationName() { return locationName; }

    /**
     * @param locationName The name of the location.
     */
    public void setLocationName(String locationName) { this.locationName = locationName; }

    /**
     * @return The physical address of the event.
     */
    public String getAddress() { return address; }

    /**
     * @param address The physical address of the event.
     */
    public void setAddress(String address) { this.address = address; }

    /**
     * @return Start time in epoch milliseconds.
     */
    public long getStartTimeMillis() { return startTimeMillis; }

    /**
     * @param startTimeMillis Start time in epoch milliseconds.
     */
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    /**
     * @return End time in epoch milliseconds.
     */
    public long getEndTimeMillis() { return endTimeMillis; }

    /**
     * @param endTimeMillis End time in epoch milliseconds.
     */
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    /**
     * @return Time when registration starts in epoch milliseconds.
     */
    public long getRegistrationOpensMillis() { return registrationOpensMillis; }

    /**
     * @param registrationOpensMillis Time when registration starts in epoch milliseconds.
     */
    public void setRegistrationOpensMillis(long registrationOpensMillis) { this.registrationOpensMillis = registrationOpensMillis; }

    /**
     * @return Time when registration ends in epoch milliseconds.
     */
    public long getRegistrationClosesMillis() { return registrationClosesMillis; }

    /**
     * @param registrationClosesMillis Time when registration ends in epoch milliseconds.
     */
    public void setRegistrationClosesMillis(long registrationClosesMillis) { this.registrationClosesMillis = registrationClosesMillis; }

    /**
     * @return Maximum number of attendees.
     */
    public int getCapacity() { return capacity; }

    /**
     * @param capacity Maximum number of attendees.
     */
    public void setCapacity(int capacity) { this.capacity = capacity; }

    /**
     * @return Number of people to be selected in the lottery.
     */
    public int getSampleSize() { return sampleSize; }

    /**
     * @param sampleSize Number of people to be selected in the lottery.
     */
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }

    /**
     * @return Optional limit for the waiting list.
     */
    public Integer getWaitlistLimit() { return waitlistLimit; }

    /**
     * @param waitlistLimit Optional limit for the waiting list.
     */
    public void setWaitlistLimit(Integer waitlistLimit) { this.waitlistLimit = waitlistLimit; }

    /**
     * @return True if geolocation is required for entrants, false otherwise.
     */
    public boolean isGeolocationRequired() { return geolocationRequired; }

    /**
     * @param geolocationRequired Whether geolocation is required for entrants.
     */
    public void setGeolocationRequired(boolean geolocationRequired) { this.geolocationRequired = geolocationRequired; }

    /**
     * @return Guidelines for the lottery process.
     */
    public String getLotteryGuidelines() { return lotteryGuidelines; }

    /**
     * @param lotteryGuidelines Guidelines for the lottery process.
     */
    public void setLotteryGuidelines(String lotteryGuidelines) { this.lotteryGuidelines = lotteryGuidelines; }

    /**
     * @return UID of the user who organized the event.
     */
    public String getOrganizerUid() { return organizerUid; }

    /**
     * @param organizerUid UID of the user who organized the event.
     */
    public void setOrganizerUid(String organizerUid) { this.organizerUid = organizerUid; }

    /**
     * @return ID of the poster image (optional).
     */
    public String getPosterImageId() { return posterImageId; }

    /**
     * @param posterImageId ID of the poster image (optional).
     */
    public void setPosterImageId(String posterImageId) { this.posterImageId = posterImageId; }

    /**
     * @return Value encoded in the QR code (optional).
     */
    public String getQrCodeValue() { return qrCodeValue; }

    /**
     * @param qrCodeValue Value encoded in the QR code (optional).
     */
    public void setQrCodeValue(String qrCodeValue) { this.qrCodeValue = qrCodeValue; }

    /**
     * @return List of interests associated with the event.
     */
    public List<String> getInterests() { return interests; }

    /**
     * @param interests List of interests associated with the event.
     */
    public void setInterests(List<String> interests) { this.interests = interests; }

    /**
     * @return True if the lottery draw has occurred, false otherwise.
     */
    public boolean isDrawHappened() { return drawHappened; }

    /**
     * @param drawHappened Whether the lottery draw has occurred.
     */
    public void setDrawHappened(boolean drawHappened) { this.drawHappened = drawHappened; }
    public boolean isPrivateEvent() { return privateEvent; }
    public void setPrivateEvent(boolean privateEvent) { this.privateEvent = privateEvent; }

    public List<String> getCoOrganizerUids() { return coOrganizerUids; }
    public void setCoOrganizerUids(List<String> coOrganizerUids) { this.coOrganizerUids = coOrganizerUids; }

    public List<String> getPendingCoOrganizerUids() { return pendingCoOrganizerUids; }
    public void setPendingCoOrganizerUids(List<String> pendingCoOrganizerUids) {
        this.pendingCoOrganizerUids = pendingCoOrganizerUids;
    }

    public boolean isCoOrganizer(String uid) {
        if (uid == null || uid.trim().isEmpty()) return false;
        if (coOrganizerUids == null) return false;
        for (String id : coOrganizerUids) {
            if (uid.equals(id)) return true;
        }
        return false;
    }

    public boolean isPendingCoOrganizer(String uid) {
        if (uid == null || uid.trim().isEmpty()) return false;
        if (pendingCoOrganizerUids == null) return false;
        for (String id : pendingCoOrganizerUids) {
            if (uid.equals(id)) return true;
        }
        return false;
    }
}
