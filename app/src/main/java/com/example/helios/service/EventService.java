package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.EventRepository;
import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Service wrapper for event-related repository operations.
 *
 * Role: thin service layer over the repository for event CRUD actions.
 * Issues: currently adds little business logic beyond delegation.
 */
/**
 * Service class that provides business logic for managing events.
 * It interacts with the {@link EventRepository} to perform CRUD operations on events.
 */
public class EventService {

    private final EventRepository repository;

    public EventService() {
        this(new FirebaseRepository());
    }

    public EventService(@NonNull EventRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves all events.
     *
     * @param onSuccess Callback receiving the list of all events.
     * @param onFailure Callback for failed operation.
     */
    public void getAllEvents(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllEvents(onSuccess, onFailure);
    }

    /**
     * Retrieves a single event by its unique identifier.
     *
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback receiving the event (null if not found).
     * @param onFailure Callback for failed operation.
     */
    public void getEventById(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Event> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getEventById(eventId, onSuccess, onFailure);
    }

    /**
     * Saves or updates an event.
     *
     * @param event     The event object to save.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void saveEvent(
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.saveEvent(event, onSuccess, onFailure);
    }

    /**
     * Deletes an event by its unique identifier.
     *
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void deleteEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.deleteEvent(eventId, onSuccess, onFailure);
    }
}
