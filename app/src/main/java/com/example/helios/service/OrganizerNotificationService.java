package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.NotificationRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Centralizes organizer triggered notification behavior
 * Service gives us one organizer side place to :
 * 1. Decide who should receive a notification
 * 2. Respect notification preferences
 * 3. Build notificationRecord objects consistently
 * 4. Support both manual organizer broadcasts and automatic workflow messages
 *
 */
public class OrganizerNotificationService {
    private final WaitingListRepository waitingListRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public OrganizerNotificationService() {
        this(new FirebaseRepository());
    }

    public OrganizerNotificationService(@NonNull FirebaseRepository repository) {
        this(repository, repository, repository);
    }

    public OrganizerNotificationService(
            @NonNull WaitingListRepository waitingListRepository,
            @NonNull NotificationRepository notificationRepository,
            @NonNull UserRepository userRepository
    ) {
        this.waitingListRepository = waitingListRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }


    /**
     * Sends a manual notification to an organizer selected audience for a given event
     * Powers the "Notify Entrants" screen, when a organizer chooses an audience
     * such as waiting, selected or cancelled, it sends a custom title and message
     *
     * Basic flow:
     * 1. Load all waiting list entries for an event
     * 2. Filters entries by the requested audience
     * 3. Collect unique recipient UIDs
     * 4. Delegates actual notification creation to sendToRecipients()
     * @param organizerUid
     * @param eventId
     * @param audience
     * @param title
     * @param message
     * @param onSuccess
     * @param onFailure
     */

    public void sendToAudience(
            @NonNull String organizerUid,
            @NonNull String eventId,
            @NonNull NotificationAudience audience,
            @NonNull String title,
            @NonNull String message,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        waitingListRepository.getAllWaitingListEntries(eventId, entries -> {
            Set<String> recipientUids = new HashSet<>();
            for (WaitingListEntry entry : entries) {
                if (entry == null || entry.getEntrantUid() == null || entry.getStatus() == null) continue;
                if (matchesAudience(entry.getStatus(), audience)) {
                    recipientUids.add(entry.getEntrantUid());
                }
            }

            // Delegate to sendToRecipients so notification preferences are checked
            // consistently with automated messages (draw results, cancellations, etc.).
            sendToRecipients(organizerUid, eventId, audience, title, message, recipientUids, onSuccess, onFailure);
        }, onFailure);
    }


    /**
     * Sends a notification to an explicit set of recipients UIDs
     * Reusable helper function
     * Notification records are only created for users whose profile exists and
     * whose notificationsEnabled flag is true.
     * @param organizerUid
     * @param eventId
     * @param audience
     * @param title
     * @param message
     * @param recipientUids
     * @param onSuccess
     * @param onFailure
     */
    private void sendToRecipients(
            @NonNull String organizerUid,
            @NonNull String eventId,
            @NonNull NotificationAudience audience,
            @NonNull String title,
            @NonNull String message,
            @NonNull Set<String> recipientUids,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (recipientUids.isEmpty()) {
            onSuccess.onSuccess(new NotificationSendResult(0));
            return;
        }
        List<NotificationRecord> records = new ArrayList<>();
        AtomicInteger remaining = new AtomicInteger(recipientUids.size());
        AtomicBoolean failed = new AtomicBoolean(false);

        for (String uid: recipientUids) {
            userRepository.getUser(uid, user -> {
                //Check if user exists and has notifications enabled
                if (user != null && user.isNotificationsEnabled()) {
                    records.add(new NotificationRecord(
                            UUID.randomUUID().toString(),
                            eventId,
                            organizerUid,
                            uid,
                            audience,
                            title,
                            message,
                            System.currentTimeMillis())
                    );
                }

                //Once every user lookup finished, save all records in one batch
                if (remaining.decrementAndGet() == 0 && !failed.get()) {
                    notificationRepository.saveNotificationsBatch(
                            records,
                            unused -> onSuccess.onSuccess(new NotificationSendResult(records.size())),
                            onFailure
                    );
                }
            }, error -> {
                if (failed.compareAndSet(false, true)) {
                    onFailure.onFailure(error);
                }

            });
        }


    }

    /**
     * Makes a single entrant as cancelled and sends them a cancellation noification
     * Expected callers:
     *      OrganizerViewEntrantsFragment when an organizer removes an invited or waiting entrant.
     *      Any future admin/organizer workflow that cancels participation.
     * @param organizerUid
     * @param event
     * @param entry
     * @param reason
     * @param onSuccess
     * @param onFailure
     */



    public void cancelEntrant(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull WaitingListEntry entry,
            @NonNull String reason,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        entry.setStatus(WaitingListStatus.CANCELLED);
        entry.setCancelledAtMillis(System.currentTimeMillis());
        entry.setStatusReason(reason);

        waitingListRepository.updateWaitingListEntry(
                event.getEventId(),
                entry.getEntrantUid(),
                entry,
                unused -> {
                    Set<String> recipients = new HashSet<>();
                    recipients.add(entry.getEntrantUid());
                    String eventTitle = describeEvent(event);

                    sendToRecipients(
                            organizerUid,
                            event.getEventId(),
                            NotificationAudience.CANCELLED,
                            titleForEvent("Registration cancelled", eventTitle),
                            "Your spot for " + eventTitle
                                    + " was cancelled. Reason: " + reason
                                    + ". Open the event for the latest details.",
                            recipients,
                            result -> onSuccess.onSuccess(null),
                            onFailure
                    );
                },
                onFailure
        );
    }

    /**
     * Send automatic notifications for a completed draw
     *
     * This method does not change waiting-list statuses by itself. Its job is only
     * to notify based on already-determined results. That separation keeps this
     * service focused: state changes happen first, then notifications are sent.
     * @param organizerUid
     * @param event
     * @param invitedEntries
     * @param notSelectedEntries
     * @param onSuccess
     * @param onFailure
     */
    public void notifyDrawResults(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull List<WaitingListEntry> invitedEntries,
            @NonNull List<WaitingListEntry> notSelectedEntries,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String eventTitle = describeEvent(event);
        Set<String> invitedUids = new HashSet<>();
        for (WaitingListEntry entry : invitedEntries) {
            invitedUids.add(entry.getEntrantUid());
        }

        Set<String> notSelectedUids = new HashSet<>();
        for (WaitingListEntry entry : notSelectedEntries) {
            notSelectedUids.add(entry.getEntrantUid());
        }

        sendToRecipients(
                organizerUid,
                event.getEventId(),
                NotificationAudience.INVITED,
                titleForEvent("Invitation ready", eventTitle),
                "You were selected in the latest draw for " + eventTitle
                        + ". Open the event to accept or decline your invitation.",
                invitedUids,
                invitedResult -> sendToRecipients(
                        organizerUid,
                        event.getEventId(),
                        NotificationAudience.NOT_SELECTED,
                        titleForEvent("Draw update", eventTitle),
                        "You were not selected in the latest draw for " + eventTitle
                                + ". If more spots open, the organizer can invite more entrants.",
                        notSelectedUids,
                        notSelectedResult -> onSuccess.onSuccess(null),
                        onFailure
                ),
                onFailure
        );
    }

    public void notifyCoOrganizerInvite(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull String recipientUid,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        sendToSingleRecipient(
                organizerUid,
                event.getEventId(),
                recipientUid,
                NotificationAudience.CO_ORGANIZER_INVITE,
                titleForEvent("Co-organizer invite", describeEvent(event)),
                "Open " + describeEvent(event)
                        + " to review the event and accept or decline the co-organizer invite.",
                onSuccess,
                onFailure
        );
    }

    public void notifyPrivateEventInvite(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull String recipientUid,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        sendToSingleRecipient(
                organizerUid,
                event.getEventId(),
                recipientUid,
                NotificationAudience.PRIVATE_EVENT_INVITE,
                titleForEvent("Private event invite", describeEvent(event)),
                "Open " + describeEvent(event)
                        + " to review the event and accept or decline your invitation.",
                onSuccess,
                onFailure
        );
    }

    public void notifyReplacementInvite(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull String recipientUid,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        sendToSingleRecipient(
                organizerUid,
                event.getEventId(),
                recipientUid,
                NotificationAudience.INVITED,
                titleForEvent("Replacement invitation ready", describeEvent(event)),
                "A spot opened up for " + describeEvent(event)
                        + ". Open the event to accept or decline your invitation.",
                onSuccess,
                onFailure
        );
    }

    public void notifyEventCancelled(
            @NonNull String organizerUid,
            @NonNull Event event,
            @NonNull List<WaitingListEntry> entries,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        Set<String> recipientUids = new HashSet<>();
        for (WaitingListEntry entry : entries) {
            if (entry == null || entry.getEntrantUid() == null) {
                continue;
            }
            recipientUids.add(entry.getEntrantUid());
        }
        sendToRecipients(
                organizerUid,
                event.getEventId(),
                NotificationAudience.CANCELLED,
                titleForEvent("Event cancelled", describeEvent(event)),
                describeEvent(event) + " was cancelled by the organizer and will no longer take place.",
                recipientUids,
                onSuccess,
                onFailure
        );
    }

    public void logEventAudit(
            @NonNull String actorUid,
            @NonNull Event event,
            @NonNull String title,
            @NonNull String message,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID().toString(),
                event.getEventId(),
                actorUid,
                null,
                NotificationAudience.AUDIENCE,
                title,
                message,
                System.currentTimeMillis()
        );
        notificationRepository.saveNotification(record, onSuccess, onFailure);
    }

    /**
     * Convience helper for sending a message to exactly one user.
     *
     * @param organizerUid
     * @param eventId
     * @param recipientUid
     * @param audience
     * @param title
     * @param message
     * @param onSuccess
     * @param onFailure
     */
    private void sendToSingleRecipient(
            @NonNull String organizerUid,
            @NonNull String eventId,
            @NonNull String recipientUid,
            @NonNull NotificationAudience audience,
            @NonNull String title,
            @NonNull String message,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        Set<String> recipients = new HashSet<>();
        recipients.add(recipientUid);
        sendToRecipients(organizerUid, eventId, audience, title, message, recipients, onSuccess, onFailure);
    }

    /**
     * Determines whether a waiting list belongs to a given organizer selected audience
     * The manual notification screen lets organizers choose a logical audience
     *  such as "Waiting List Entrants" or "Selected Entrants." This helper converts
     *  those audience choices into actual waiting-list status checks.
     * @param status
     * @param audience
     * @return
     */
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

    @NonNull
    private String describeEvent(@NonNull Event event) {
        String title = event.getTitle();
        if (title == null || title.trim().isEmpty()) {
            return "this event";
        }
        return title.trim();
    }

    @NonNull
    private String titleForEvent(@NonNull String prefix, @NonNull String eventTitle) {
        if ("this event".equals(eventTitle)) {
            return prefix;
        }
        return prefix + ": " + eventTitle;
    }
}
