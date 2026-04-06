package com.example.helios.data;

import androidx.annotation.NonNull;

import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Repository interface for waiting list persistence operations.
 */
public interface WaitingListRepository {

    void getAllWaitingListEntries(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<WaitingListEntry> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void upsertWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void updateWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getWaitlistEntriesForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    /**
     * Counts waiting-list entries for an event that have the given status.
     * More efficient than getAllWaitingListEntries when only a count is needed.
     */
    void getWaitingEntriesCount(
            @NonNull String eventId,
            @NonNull WaitingListStatus status,
            @NonNull OnSuccessListener<Integer> onSuccess,
            @NonNull OnFailureListener onFailure
    );
}
