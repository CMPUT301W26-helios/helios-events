package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OrganizerNotificationService {
    private final FirebaseRepository repository = new FirebaseRepository();

    public void sendToAudience(
            @NonNull String organizerUid,
            @NonNull String eventId,
            @NonNull NotificationAudience audience,
            @NonNull String title,
            @NonNull String message,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllWaitingListEntries(eventId, entries -> {
            Set<String> recipientUids = new HashSet<>();
            for (WaitingListEntry entry : entries) {
                if (entry == null || entry.getEntrantUid() == null || entry.getStatus() == null) continue;
                if (matchesAudience(entry.getStatus(), audience)) {
                    recipientUids.add(entry.getEntrantUid());
                }
            }

            if (recipientUids.isEmpty()) {
                onSuccess.onSuccess(new NotificationSendResult(0));
                return;
            }

            List<NotificationRecord> records = new ArrayList<>();
            for (String uid : recipientUids) {
                records.add(new NotificationRecord(
                        UUID.randomUUID().toString(),
                        eventId,
                        organizerUid,
                        uid,
                        audience,
                        title,
                        message,
                        System.currentTimeMillis()
                ));
            }

            repository.saveNotificationsBatch(
                    records,
                    unused -> onSuccess.onSuccess(new NotificationSendResult(records.size())),
                    onFailure
            );
        }, onFailure);
    }

    private boolean matchesAudience(@NonNull WaitingListStatus status, @NonNull NotificationAudience audience) {
        if (audience == NotificationAudience.WAITING) {
            return status == WaitingListStatus.WAITING;
        }
        if (audience == NotificationAudience.SELECTED) {
            return status == WaitingListStatus.INVITED || status == WaitingListStatus.ACCEPTED;
        }
        if (audience == NotificationAudience.CANCELLED) {
            return status == WaitingListStatus.CANCELLED;
        }
        return false;
    }
}
