package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

public class WaitingListService {
    private final FirebaseRepository repository;

    public WaitingListService() {
        this.repository = new FirebaseRepository();
    }

    public void getEntriesForEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllWaitingListEntries(eventId, onSuccess, onFailure);
    }

    public void updateEntry(
            @NonNull String eventId,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (entry.getEntrantUid() == null || entry.getEntrantUid().trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("entrantUid must not be empty."));
            return;
        }
        repository.updateWaitingListEntry(eventId, entry.getEntrantUid(), entry, onSuccess, onFailure);
    }

    public void removeEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.deleteWaitingListEntry(eventId, entrantUid, onSuccess, onFailure);
    }
}
