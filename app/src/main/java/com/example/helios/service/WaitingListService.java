package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Service class that provides business logic for managing event waiting lists.
 * It interacts with the {@link FirebaseRepository} to perform operations on waiting list entries.
 */
public class WaitingListService {

    private final FirebaseRepository repository;

    /**
     * Initializes the WaitingListService with a new FirebaseRepository instance.
     */
    public WaitingListService() {
        this(new FirebaseRepository());
    }

    // Package-private test seam
    WaitingListService(@NonNull FirebaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves all waiting list entries for a specific event.
     *
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback receiving the list of waiting list entries.
     * @param onFailure Callback for failed operation.
     */
    public void getEntriesForEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllWaitingListEntries(eventId, onSuccess, onFailure);
    }

    /**
     * Updates an existing waiting list entry.
     *
     * @param eventId   The unique identifier for the event.
     * @param entry     The updated waiting list entry.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void updateEntry(
            @NonNull String eventId,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String entrantUid = entry.getEntrantUid();
        if (entrantUid == null || entrantUid.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("entrantUid must not be empty."));
            return;
        }

        repository.updateWaitingListEntry(eventId, entrantUid, entry, onSuccess, onFailure);
    }

    /**
     * Removes an entry from a waiting list.
     *
     * @param eventId    The unique identifier for the event.
     * @param entrantUid The unique identifier for the entrant.
     * @param onSuccess  Callback for successful operation.
     * @param onFailure  Callback for failed operation.
     */
    public void removeEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.deleteWaitingListEntry(eventId, entrantUid, onSuccess, onFailure);
    }
}