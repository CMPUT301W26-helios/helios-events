package com.example.helios.data;

import androidx.annotation.NonNull;

import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Repository interface for event persistence operations.
 */
public interface EventRepository {

    void getAllEvents(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getEventById(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Event> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void saveEvent(
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteEventsByOrganizer(
            @NonNull String organizerUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getEventsWithPosters(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    );
}
