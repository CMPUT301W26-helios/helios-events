package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * Service for organizer-side waiting-list retrieval, update, and removal actions.
 *
 * Role: control/service layer for waiting-list management.
 * Issues: validation is minimal and most operations delegate directly to the repository.
 *
 * Alt Description:
 * Service class that provides business logic for managing event waiting lists.
 * It interacts with the {@link WaitingListRepository} to perform operations on waiting list entries.
 */
public class WaitingListService {

    private final WaitingListRepository repository;

    public WaitingListService() {
        this(new FirebaseRepository());
    }

    public WaitingListService(@NonNull WaitingListRepository repository) {
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

    /**
     * Automatically invites one replacement entrant from the NOT_SELECTED pool.
     * Used to trigger a replacement draw when a winner declines their invitation.
     *
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback called with true if a replacement was invited, false if pool is empty.
     * @param onFailure Callback for failed operation.
     */
    public void autoInviteReplacement(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllWaitingListEntries(eventId, entries -> {
            List<WaitingListEntry> notSelected = new ArrayList<>();
            for (WaitingListEntry entry : entries) {
                if (entry != null && entry.getStatus() == WaitingListStatus.NOT_SELECTED) {
                    notSelected.add(entry);
                }
            }
            if (notSelected.isEmpty()) {
                onSuccess.onSuccess(false);
                return;
            }
            Collections.shuffle(notSelected);
            WaitingListEntry replacement = notSelected.get(0);
            long now = System.currentTimeMillis();
            replacement.setStatus(WaitingListStatus.INVITED);
            replacement.setInvitedAtMillis(now);
            replacement.setStatusReason("Replacement draw after decline");
            repository.upsertWaitingListEntry(
                    eventId, replacement.getEntrantUid(), replacement,
                    unused -> onSuccess.onSuccess(true),
                    onFailure
            );
        }, onFailure);
    }

    public void inviteEntrantToWaitingList(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getWaitingListEntry(eventId, entrantUid, existing -> {
            if (existing != null
                    && existing.getStatus() != null
                    && existing.getStatus() != WaitingListStatus.CANCELLED
                    && existing.getStatus() != WaitingListStatus.NOT_SELECTED
                    && existing.getStatus() != WaitingListStatus.DECLINED) {
                onFailure.onFailure(new IllegalStateException("User is already on this waiting list."));
                return;
            }

            WaitingListEntry entry = existing != null ? existing : new WaitingListEntry();
            entry.setEventId(eventId);
            entry.setEntrantUid(entrantUid);
            long now = System.currentTimeMillis();
            entry.setStatus(WaitingListStatus.INVITED);
            if (entry.getJoinedAtMillis() <= 0) {
                entry.setJoinedAtMillis(now);
            }
            entry.setInvitedAtMillis(now);
            repository.upsertWaitingListEntry(eventId, entrantUid, entry, onSuccess, onFailure);
        }, onFailure);
    }
}
