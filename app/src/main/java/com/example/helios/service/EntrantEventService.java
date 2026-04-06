package com.example.helios.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.EventRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
/**
 * Service for entrant-side event participation: joining/leaving the waiting list,
 * accepting/declining invitations, and querying the current user's entry status.
 */
public class EntrantEventService {

    private final WaitingListRepository waitingListRepository;
    private final EventRepository eventRepository;
    private final ProfileService profileService;

    public EntrantEventService() {
        this(new FirebaseRepository());
    }

    public EntrantEventService(@NonNull FirebaseRepository repository) {
        this(repository, repository, new ProfileService(repository));
    }

    public EntrantEventService(@NonNull FirebaseRepository repository, @NonNull ProfileService profileService) {
        this(repository, repository, profileService);
    }

    public EntrantEventService(
            @NonNull WaitingListRepository waitingListRepository,
            @NonNull EventRepository eventRepository,
            @NonNull ProfileService profileService
    ) {
        this.waitingListRepository = waitingListRepository;
        this.eventRepository = eventRepository;
        this.profileService = profileService;
    }

    /**
     * Adds the current user to the waiting list for a specific event.
     *
     * @param eventId   The unique identifier for the event.
     * @param latitude  Optional latitude captured when the event requires geolocation.
     * @param longitude Optional longitude captured when the event requires geolocation.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void joinWaitingList(
            @NonNull String eventId,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        profileService.ensureSignedIn(
                firebaseUser -> doJoin(eventId, latitude, longitude, firebaseUser, onSuccess, onFailure),
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
        waitingListRepository.upsertWaitingListEntry(
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
                firebaseUser -> waitingListRepository.getWaitingListEntry(eventId, firebaseUser.getUid(), onSuccess, onFailure),
                onFailure
        );
    }

    /**
     * Retrieves all waiting-list entries that belong to the current user across events.
     *
     * @param context   The application context.
     * @param onSuccess Callback receiving the user's entries.
     * @param onFailure Callback for failed operation.
     */
    public void getCurrentUserWaitlistEntries(
            @NonNull Context context,
            @NonNull OnSuccessListener<java.util.List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        profileService.ensureSignedIn(
                firebaseUser -> waitingListRepository.getWaitlistEntriesForUser(firebaseUser.getUid(), onSuccess, onFailure),
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
        waitingListRepository.getAllWaitingListEntries(eventId, entries -> {
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
            @Nullable Double latitude,
            @Nullable Double longitude,
            @NonNull FirebaseUser firebaseUser,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String uid = firebaseUser.getUid();
        long now = System.currentTimeMillis();

        eventRepository.getEventById(eventId, event -> {
            if (event == null) {
                onFailure.onFailure(new IllegalArgumentException("Event not found."));
                return;
            }
            if (uid.equals(event.getOrganizerUid()) || event.isCoOrganizer(uid)) {
                onFailure.onFailure(new SecurityException("Organizers/co-organizers cannot join the entrant pool for this event."));
                return;
            }
            if (event.isGeolocationRequired() && (latitude == null || longitude == null)) {
                onFailure.onFailure(new IllegalStateException("This event requires location access to join."));
                return;
            }
            if (event.isGeolocationRequired() && event.hasGeofence() && latitude != null && longitude != null) {
                Double distanceMeters = calculateDistanceMeters(
                        latitude,
                        longitude,
                        event.getGeofenceCenter().getLatitude(),
                        event.getGeofenceCenter().getLongitude()
                );
                Integer radiusMeters = event.getGeofenceRadiusMeters();
                if (distanceMeters != null && radiusMeters != null && distanceMeters > radiusMeters) {
                    onFailure.onFailure(new IllegalStateException(
                            "You must be within " + radiusMeters + " meters of the event location to join."
                    ));
                    return;
                }
            }

            waitingListRepository.getWaitingListEntry(eventId, uid, existing -> {
                if (existing != null
                        && existing.getStatus() != null
                        && existing.getStatus() != WaitingListStatus.CANCELLED
                        && existing.getStatus() != WaitingListStatus.NOT_SELECTED
                        && existing.getStatus() != WaitingListStatus.DECLINED) {
                    onSuccess.onSuccess(null);
                    return;
                }

                Integer waitlistLimit = event.getWaitlistLimit();
                if (waitlistLimit != null && waitlistLimit > 0) {
                    waitingListRepository.getWaitingEntriesCount(eventId, WaitingListStatus.WAITING, count -> {
                        if (count >= waitlistLimit) {
                            onFailure.onFailure(new IllegalStateException("The waiting list for this event is full."));
                        } else {
                            proceedWithJoin(eventId, uid, now, latitude, longitude, onSuccess, onFailure);
                        }
                    }, onFailure);
                } else {
                    proceedWithJoin(eventId, uid, now, latitude, longitude, onSuccess, onFailure);
                }
            }, onFailure);
        }, onFailure);
    }

    private void proceedWithJoin(
            @NonNull String eventId,
            @NonNull String uid,
            long now,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEventId(eventId);
        entry.setEntrantUid(uid);
        entry.setStatus(WaitingListStatus.WAITING);
        entry.setJoinedAtMillis(now);
        if (latitude != null && longitude != null) {
            entry.setJoinLatitude(latitude);
            entry.setJoinLongitude(longitude);
        }

        waitingListRepository.upsertWaitingListEntry(eventId, uid, entry, onSuccess, onFailure);
    }

    @Nullable
    static Double calculateDistanceMeters(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        double earthRadiusMeters = 6_371_000d;
        double startLatitudeRadians = Math.toRadians(startLatitude);
        double endLatitudeRadians = Math.toRadians(endLatitude);
        double deltaLatitudeRadians = Math.toRadians(endLatitude - startLatitude);
        double deltaLongitudeRadians = Math.toRadians(endLongitude - startLongitude);

        double haversine = Math.sin(deltaLatitudeRadians / 2d) * Math.sin(deltaLatitudeRadians / 2d)
                + Math.cos(startLatitudeRadians) * Math.cos(endLatitudeRadians)
                * Math.sin(deltaLongitudeRadians / 2d) * Math.sin(deltaLongitudeRadians / 2d);
        double arc = 2d * Math.atan2(Math.sqrt(haversine), Math.sqrt(1d - haversine));
        return earthRadiusMeters * arc;
    }

    private void doLeave(
            @NonNull String eventId,
            @NonNull FirebaseUser firebaseUser,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String uid = firebaseUser.getUid();

        waitingListRepository.getWaitingListEntry(eventId, uid, existing -> {
            if (existing == null) {
                onSuccess.onSuccess(null);
                return;
            }

            existing.setStatus(WaitingListStatus.CANCELLED);
            waitingListRepository.updateWaitingListEntry(eventId, uid, existing, onSuccess, onFailure);
        }, onFailure);
    }
}
