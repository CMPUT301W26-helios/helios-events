package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

public class EventService {
    private final FirebaseRepository repository;

    public EventService() {
        this.repository = new FirebaseRepository();
    }

    public void getAllEvents(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllEvents(onSuccess, onFailure);
    }

    public void getEventById(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Event> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getEventById(eventId, onSuccess, onFailure);
    }

    public void saveEvent(
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.saveEvent(event, onSuccess, onFailure);
    }

    public void deleteEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.deleteEvent(eventId, onSuccess, onFailure);
    }
}