package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.EventRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LotteryService {
    static final String NO_PEOPLE_IN_EVENT_MESSAGE = "There are no people in this event";

    private final WaitingListRepository waitingListRepository;
    private final EventRepository eventRepository;
    private final OrganizerNotificationService organizerNotificationService;

    public LotteryService() {
        this(new FirebaseRepository());
    }

    public LotteryService(@NonNull FirebaseRepository repository) {
        this(repository, repository, new OrganizerNotificationService(repository));
    }

    public LotteryService(
            @NonNull WaitingListRepository waitingListRepository,
            @NonNull EventRepository eventRepository,
            @NonNull OrganizerNotificationService organizerNotificationService
    ) {
        this.waitingListRepository = waitingListRepository;
        this.eventRepository = eventRepository;
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

        waitingListRepository.getAllWaitingListEntries(eventId, entries -> {
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

            AtomicInteger pending = new AtomicInteger(winners.size() + losers.size());
            if (pending.get() == 0) {
                finalizeEventAndNotify(organizerUid, event, notifiedWinners, notifiedLosers, onSuccess, onFailure);
                return;
            }

            Runnable checkDone = () -> {
                if (pending.decrementAndGet() == 0) {
                    finalizeEventAndNotify(organizerUid, event, notifiedWinners, notifiedLosers, onSuccess, onFailure);
                }
            };

            for (WaitingListEntry winner : winners) {
                winner.setStatus(WaitingListStatus.INVITED);
                winner.setInvitedAtMillis(now);
                winner.setStatusReason("Selected in draw");

                waitingListRepository.upsertWaitingListEntry(eventId, winner.getEntrantUid(), winner,
                        unused -> {
                            notifiedWinners.add(winner);
                            checkDone.run();
                        },
                        error -> checkDone.run());
            }

            for (WaitingListEntry loser : losers) {
                loser.setStatus(WaitingListStatus.NOT_SELECTED);
                loser.setStatusReason("Not selected in current draw");

                waitingListRepository.upsertWaitingListEntry(eventId, loser.getEntrantUid(), loser,
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
        event.setDrawCount(event.getDrawCount() + 1);
        eventRepository.saveEvent(event, unused ->
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
