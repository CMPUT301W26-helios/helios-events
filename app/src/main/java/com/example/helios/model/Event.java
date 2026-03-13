package com.example.helios.model;

import java.util.List;

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

    // Construction:
    public Event() {}

    public Event(String eventId, String title, String description, String locationName, String address,
                 long startTimeMillis, long endTimeMillis, long registrationOpensMillis, long registrationClosesMillis,
                 int capacity, int sampleSize, Integer waitlistLimit, boolean geolocationRequired,
                 String lotteryGuidelines, String organizerUid, String posterImageId, String qrCodeValue,List<String> interests) {
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
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    public long getRegistrationOpensMillis() { return registrationOpensMillis; }
    public void setRegistrationOpensMillis(long registrationOpensMillis) { this.registrationOpensMillis = registrationOpensMillis; }

    public long getRegistrationClosesMillis() { return registrationClosesMillis; }
    public void setRegistrationClosesMillis(long registrationClosesMillis) { this.registrationClosesMillis = registrationClosesMillis; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getSampleSize() { return sampleSize; }
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }

    public Integer getWaitlistLimit() { return waitlistLimit; }
    public void setWaitlistLimit(Integer waitlistLimit) { this.waitlistLimit = waitlistLimit; }

    public boolean isGeolocationRequired() { return geolocationRequired; }
    public void setGeolocationRequired(boolean geolocationRequired) { this.geolocationRequired = geolocationRequired; }

    public String getLotteryGuidelines() { return lotteryGuidelines; }
    public void setLotteryGuidelines(String lotteryGuidelines) { this.lotteryGuidelines = lotteryGuidelines; }

    public String getOrganizerUid() { return organizerUid; }
    public void setOrganizerUid(String organizerUid) { this.organizerUid = organizerUid; }

    public String getPosterImageId() { return posterImageId; }
    public void setPosterImageId(String posterImageId) { this.posterImageId = posterImageId; }

    public String getQrCodeValue() { return qrCodeValue; }
    public void setQrCodeValue(String qrCodeValue) { this.qrCodeValue = qrCodeValue; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
    public boolean isDrawHappened() { return drawHappened; }
    public void setDrawHappened(boolean drawHappened) { this.drawHappened = drawHappened; }
}