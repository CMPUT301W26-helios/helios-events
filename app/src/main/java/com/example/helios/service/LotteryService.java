package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LotteryService {
    static final String NO_PEOPLE_IN_EVENT_MESSAGE = "There are no people in this event";

    private final FirebaseRepository repository;
    private final OrganizerNotificationService organizerNotificationService;

    public LotteryService() {

        this(new FirebaseRepository(), new OrganizerNotificationService());
    }

    LotteryService(@NonNull FirebaseRepository repository, @NonNull OrganizerNotificationService organizerNotificationService) {
        this.repository = repository;
        this.organizerNotificationService = organizerNotificationService;
    }

    public void runDraw(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        int defaultTargetCount = event.getSampleSize() > 0 ? event.getSampleSize() : event.getCapacity();
        runDraw(organizerUid, event, defaultTargetCount, onSuccess, onFailure);
    }

    public void runDraw(
            @NonNull String organizerUid,
            @NonNull Event event,
            int targetCount,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String eventId = event.getEventId();
        int sampleSize = Math.max(0, targetCount);

        repository.getAllWaitingListEntries(eventId, entries -> {
            List<WaitingListEntry> waiting = new ArrayList<>();
            for (WaitingListEntry e : entries) {
                if (e != null && e.getStatus() == WaitingListStatus.WAITING) {
                    waiting.add(e);
                }
            }

            if (waiting.isEmpty()) {
                onFailure.onFailure(new IllegalStateException(NO_PEOPLE_IN_EVENT_MESSAGE));
                return;
            }

            if (sampleSize <= 0) {
                onFailure.onFailure(new IllegalArgumentException("Draw count must be greater than 0."));
                return;
            }

            Collections.shuffle(waiting);

            List<WaitingListEntry> winners = new ArrayList<>(
                    waiting.subList(0, Math.min(sampleSize, waiting.size()))
            );
            List<WaitingListEntry> losers = new ArrayList<>(
                    waiting.subList(winners.size(), waiting.size())
            );

            long now = System.currentTimeMillis();
            List<WaitingListEntry> notifiedWinners = Collections.synchronizedList(new ArrayList<>());
            List<WaitingListEntry> notifiedLosers = Collections.synchronizedList(new ArrayList<>());

            int[] pending = {winners.size() + losers.size()};
            if (pending[0] == 0) {
                finalizeEventAndNotify(organizerUid, event, notifiedWinners, notifiedLosers, onSuccess, onFailure);
                return;
            }

            Runnable checkDone = () -> {
                pending[0]--;
                if (pending[0] == 0) {
                    finalizeEventAndNotify(organizerUid, event, notifiedWinners, notifiedLosers, onSuccess, onFailure);
                }
            };

            for (WaitingListEntry winner : winners) {
                winner.setStatus(WaitingListStatus.INVITED);
                winner.setInvitedAtMillis(now);
                winner.setStatusReason("Selected in draw");

                repository.upsertWaitingListEntry(eventId, winner.getEntrantUid(), winner,
                        unused -> {
                            notifiedWinners.add(winner);
                            checkDone.run();
                        },
                        error -> checkDone.run());
            }

            for (WaitingListEntry loser : losers) {
                loser.setStatus(WaitingListStatus.NOT_SELECTED);
                loser.setStatusReason("Not selected in current draw");

                repository.upsertWaitingListEntry(eventId, loser.getEntrantUid(), loser,
                        unused -> {
                            notifiedLosers.add(loser);
                            checkDone.run();
                        },
                        error -> checkDone.run());
            }

        }, onFailure);
    }


    private void finalizeEventAndNotify(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull List<WaitingListEntry> winners,
            @NonNull List<WaitingListEntry> losers,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        event.setDrawHappened(true);
        repository.saveEvent(event, unused ->
                        organizerNotificationService.notifyDrawResults(
                                organizerUid,
                                event,
                                winners,
                                losers,
                                onSuccess,
                                onFailure
                        ),
                onFailure
        );
    }
}
