package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
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
public class EventService {

    private final FirebaseRepository repository;

    public EventService() {
        this(new FirebaseRepository());
    }

    // Package-private so tests in the same package can inject a mock.
    EventService(@NonNull FirebaseRepository repository) {
        this.repository = repository;
    }

    public void getAllEvents(@NonNull OnSuccessListener<List<Event>> onSuccess,
                             @NonNull OnFailureListener onFailure) {
        repository.getAllEvents(onSuccess, onFailure);
    }

    public void getEventById(@NonNull String eventId,
                             @NonNull OnSuccessListener<Event> onSuccess,
                             @NonNull OnFailureListener onFailure) {
        repository.getEventById(eventId, onSuccess, onFailure);
    }

    public void saveEvent(@NonNull Event event,
                          @NonNull OnSuccessListener<Void> onSuccess,
                          @NonNull OnFailureListener onFailure) {
        repository.saveEvent(event, onSuccess, onFailure);
    }

    public void deleteEvent(@NonNull String eventId,
                            @NonNull OnSuccessListener<Void> onSuccess,
                            @NonNull OnFailureListener onFailure) {
        repository.deleteEvent(eventId, onSuccess, onFailure);
    }
}