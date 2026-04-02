package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Service for admin-side image management.
 *
 * Role: thin service layer for listing events that have poster images and
 * removing a poster from an event. Posters are stored as local URIs on the
 * Event document (posterImageId field) — there is no separate image collection
 * or Firebase Storage bucket involved.
 */
public class ImageService {

    private final FirebaseRepository repository;

    public ImageService() {
        this(new FirebaseRepository());
    }

    // Package-private test seam
    ImageService(@NonNull FirebaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves all events that have a non-empty posterImageId.
     *
     * @param onSuccess Callback receiving the filtered list of Events.
     * @param onFailure Callback for failed operation.
     */
    public void getAllImages(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getEventsWithPosters(onSuccess, onFailure);
    }

    /**
     * Removes the poster from an event by nulling its posterImageId field.
     *
     * @param event     The event whose poster should be removed.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void deleteImage(
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }
        repository.removeEventPoster(eventId, onSuccess, onFailure);
    }
}