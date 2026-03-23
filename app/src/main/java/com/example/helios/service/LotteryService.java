package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class LotteryService {

    private final FirebaseRepository repository;

    public LotteryService() {
        this(new FirebaseRepository());
    }

    LotteryService(@NonNull FirebaseRepository repository) {
        this.repository = repository;
    }

    public void runDraw(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String eventId = event.getEventId();
        int sampleSize = event.getSampleSize() > 0 ? event.getSampleSize() : event.getCapacity();

        repository.getAllWaitingListEntries(eventId, entries -> {
            List<WaitingListEntry> waiting = new ArrayList<>();
            for (WaitingListEntry e : entries) {
                if (e.getStatus() == WaitingListStatus.WAITING) {
                    waiting.add(e);
                }
            }

            Collections.shuffle(waiting);

            List<WaitingListEntry> winners = waiting.subList(
                    0, Math.min(sampleSize, waiting.size()));
            List<WaitingListEntry> losers = waiting.subList(
                    winners.size(), waiting.size());

            int[] pending = {winners.size() + losers.size()};
            if (pending[0] == 0) {
                finalizeEvent(organizerUid, event, onSuccess, onFailure);
                return;
            }

            Runnable checkDone = () -> {
                pending[0]--;
                if (pending[0] == 0) {
                    finalizeEvent(organizerUid, event, onSuccess, onFailure);
                }
            };

            for (WaitingListEntry winner : winners) {
                winner.setStatus(WaitingListStatus.INVITED);
                winner.setInvitedAtMillis(System.currentTimeMillis());
                repository.upsertWaitingListEntry(eventId, winner.getEntrantUid(), winner,
                        unused -> {
                            sendNotification(organizerUid, eventId,
                                    winner.getEntrantUid(),
                                    "You've been selected! 🎉",
                                    "You were chosen for: " + event.getTitle()
                                            + ". Open the app to accept or decline.",
                                    NotificationAudience.INVITED);
                            checkDone.run();
                        }, e -> checkDone.run());
            }

            for (WaitingListEntry loser : losers) {
                loser.setStatus(WaitingListStatus.NOT_SELECTED);
                repository.upsertWaitingListEntry(eventId, loser.getEntrantUid(), loser,
                        unused -> {
                            sendNotification(organizerUid, eventId,
                                    loser.getEntrantUid(),
                                    "Lottery result for " + event.getTitle(),
                                    "Unfortunately you were not selected this time.",
                                    NotificationAudience.NOT_SELECTED);
                            checkDone.run();
                        }, e -> checkDone.run());
            }

        }, onFailure);
    }

    private void finalizeEvent(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        event.setDrawHappened(true);
        repository.saveEvent(event, onSuccess, onFailure);
    }

    private void sendNotification(
            @NonNull String senderUid,
            @NonNull String eventId,
            @NonNull String recipientUid,
            @NonNull String title,
            @NonNull String message,
            @NonNull NotificationAudience audience
    ) {
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID().toString(),
                eventId,
                senderUid,
                recipientUid,
                audience,
                title,
                message,
                System.currentTimeMillis()
        );
        repository.saveNotification(record, unused -> {}, e -> {});
    }
}