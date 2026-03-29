package com.example.helios.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
/**
 * Service for entrant-side event participation actions such as joining, leaving,
 * and querying the current user's waiting-list status.
 *
 * Role: application service coordinating profile identity with waiting-list persistence.
 * Issues: dependency construction is hard-wired and core lottery rules are still coupled to persistence callbacks.
 * Alt Description:
 * Service class that provides business logic for entrants interacting with events.
 * It handles operations such as joining/leaving a waiting list and checking status.
 */
public class EntrantEventService {

    private final FirebaseRepository repository;
    private final ProfileService profileService;

    public EntrantEventService() {
        this(new FirebaseRepository(), new ProfileService());
    }

    // Package-private test seam
    EntrantEventService(
            @NonNull FirebaseRepository repository,
            @NonNull ProfileService profileService
    ) {
        this.repository = repository;
        this.profileService = profileService;
    }

    /**
     * Adds the current user to the waiting list for a specific event.
     *
     * @param context   The application context.
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void joinWaitingList(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        profileService.ensureSignedIn(
                firebaseUser -> doJoin(eventId, firebaseUser, onSuccess, onFailure),
                onFailure
        );
    }
    public void updateEntry(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.upsertWaitingListEntry(
                eventId, entry.getEntrantUid(), entry, onSuccess, onFailure);
    }

    /**
     * Sets the current user's status to CANCELLED for a specific event's waiting list.
     *
     * @param context   The application context.
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void leaveWaitingList(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        profileService.ensureSignedIn(
                firebaseUser -> doLeave(eventId, firebaseUser, onSuccess, onFailure),
                onFailure
        );
    }

    /**
     * Retrieves the current user's waiting list entry for a specific event.
     *
     * @param context   The application context.
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback receiving the WaitingListEntry.
     * @param onFailure Callback for failed operation.
     */
    public void getCurrentUserWaitingListEntry(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull OnSuccessListener<WaitingListEntry> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        profileService.ensureSignedIn(
                firebaseUser -> repository.getWaitingListEntry(eventId, firebaseUser.getUid(), onSuccess, onFailure),
                onFailure
        );
    }

    /**
     * Calculates the number of filled slots for an event based on waiting list statuses.
     * Counts entries with WAITING, INVITED, or ACCEPTED status.
     *
     * @param eventId   The unique identifier for the event.
     * @param onSuccess Callback receiving the count of filled slots.
     * @param onFailure Callback for failed operation.
     */
    public void getFilledSlotsCount(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Integer> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllWaitingListEntries(eventId, entries -> {
            int count = 0;
            for (WaitingListEntry entry : entries) {
                if (entry == null || entry.getStatus() == null) continue;

                WaitingListStatus status = entry.getStatus();
                if (status == WaitingListStatus.WAITING
                        || status == WaitingListStatus.INVITED
                        || status == WaitingListStatus.ACCEPTED) {
                    count++;
                }
            }
            onSuccess.onSuccess(count);
        }, onFailure);
    }

    private void doJoin(
            @NonNull String eventId,
            @NonNull FirebaseUser firebaseUser,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String uid = firebaseUser.getUid();
        long now = System.currentTimeMillis();

        repository.getWaitingListEntry(eventId, uid, existing -> {
            if (existing != null
                    && existing.getStatus() != null
                    && existing.getStatus() != WaitingListStatus.CANCELLED
                    && existing.getStatus() != WaitingListStatus.NOT_SELECTED
                    && existing.getStatus() != WaitingListStatus.DECLINED) {
                onSuccess.onSuccess(null);
                return;
            }

            WaitingListEntry entry = new WaitingListEntry();
            entry.setEventId(eventId);
            entry.setEntrantUid(uid);
            entry.setStatus(WaitingListStatus.WAITING);
            entry.setJoinedAtMillis(now);

            repository.upsertWaitingListEntry(eventId, uid, entry, onSuccess, onFailure);
        }, onFailure);
    }

    private void doLeave(
            @NonNull String eventId,
            @NonNull FirebaseUser firebaseUser,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String uid = firebaseUser.getUid();

        repository.getWaitingListEntry(eventId, uid, existing -> {
            if (existing == null) {
                onSuccess.onSuccess(null);
                return;
            }

            existing.setStatus(WaitingListStatus.CANCELLED);
            repository.updateWaitingListEntry(eventId, uid, existing, onSuccess, onFailure);
        }, onFailure);
    }
}
