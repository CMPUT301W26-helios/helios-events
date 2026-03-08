package com.example.helios.model;

import java.util.Date;
import java.util.List;

public class Event {

    private String id;
    private String title;
    private String description;
    private Date startDate;
    private List<String> tags;

    private String organizerId;
    private int maxEntrants;
    private Date createdAt;
    private String location;
    private String imageUrl;

    // Required empty constructor for Firestore
    public Event() {}

    public Event(String id, String title, String description, Date startDate,
                 List<String> tags, String organizerId, int maxEntrants,
                 Date createdAt, String location, String imageUrl) {

        this.id = id;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.tags = tags;
        this.organizerId = organizerId;
        this.maxEntrants = maxEntrants;
        this.createdAt = createdAt;
        this.location = location;
        this.imageUrl = imageUrl;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public int getMaxEntrants() {
        return maxEntrants;
    }

    public void setMaxEntrants(int maxEntrants) {
        this.maxEntrants = maxEntrants;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}