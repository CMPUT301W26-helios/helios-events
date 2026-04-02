package com.example.helios.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.Event;
import com.example.helios.model.EventComment;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.ImageAsset;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Repository class responsible for interacting with Firebase Firestore.
 * (Handles CRUD operations for users, events, and waiting lists.)
 * This repository is responsible for Firestore reads and writes for users, events, waiting-list entries, and admin-device checks.
 *
 * Role: repository/data-access layer used by service classes.
 * Outstanding issues: validation rules are intentionally minimal, several methods are direct pass-throughs,
 * and the class directly constructs FirebaseFirestore which limits testability.
 */
public class FirebaseRepository {
    private final FirebaseFirestore db;

    /**
     * Initializes the FirebaseRepository with a Firestore instance.
     */
    public FirebaseRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // USERS SECTION:

    /**
     * Saves a user profile to Firestore.
     *
     * @param user      The user profile to save.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void saveUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isValidUser(user)) {
            onFailure.onFailure(new IllegalArgumentException("Invalid user profile."));
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Updates an existing user profile in Firestore.
     *
     * @param user      The updated user profile.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void updateUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        saveUser(user, onSuccess, onFailure);
    }

    /**
     * Retrieves a user profile from Firestore by UID.
     *
     * @param uid       The unique identifier of the user.
     * @param onSuccess Callback receiving the UserProfile (null if not found).
     * @param onFailure Callback for failed operation.
     */
    public void getUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    UserProfile user = null;
                    if (snapshot.exists()) {
                        user = snapshot.toObject(UserProfile.class);
                    }
                    onSuccess.onSuccess(user);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Deletes a user profile from Firestore by UID.
     *
     * @param uid       The unique identifier of the user.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void deleteUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        db.collection("users")
                .document(uid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Disables notifications for a specific user.
     *
     * @param uid       The unique identifier of the user.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void muteNotifications(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        db.collection("users")
                .document(uid)
                .update("notificationsEnabled", false)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Checks if a device installation ID is registered as an admin device and is enabled.
     *
     * @param installationId The device's installation ID.
     * @param onSuccess      Callback receiving true if the device is an enabled admin.
     * @param onFailure      Callback for failed operation.
     */
    public void isAdminInstallation(
            @NonNull String installationId,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(installationId)) {
            onFailure.onFailure(new IllegalArgumentException("Installation ID must not be empty."));
            return;
        }

        db.collection("admin_devices")
                .document(installationId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean isAdmin = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("enabled"));
                    onSuccess.onSuccess(isAdmin);
                })
                .addOnFailureListener(onFailure);
    }

    // EVENTS SECTION:

    /**
     * Retrieves all events from Firestore, ordered by start time.
     *
     * @param onSuccess Callback receiving a list of all events.
     * @param onFailure Callback for failed operation.
     */
    public void getAllEvents(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        // Querying all events. Filtering for 'privateEvent' should be handled by the UI
        // depending on the context (e.g., browsing vs. managing).
        db.collection("events")
                .orderBy("startTimeMillis")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Event event = doc.toObject(Event.class);
                        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                            event.setEventId(doc.getId());
                        }
                        events.add(event);
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Retrieves a single event by its ID.
     *
     * @param eventId   The unique identifier of the event.
     * @param onSuccess Callback receiving the event (null if not found).
     * @param onFailure Callback for failed operation.
     */
    public void getEventById(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Event> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("Event ID must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Event event = null;
                    if (snapshot.exists()) {
                        event = snapshot.toObject(Event.class);
                        if (event != null && (event.getEventId() == null || event.getEventId().trim().isEmpty())) {
                            event.setEventId(snapshot.getId());
                        }
                    }
                    onSuccess.onSuccess(event);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Saves or updates an event in Firestore.
     * If the event has no ID, a new document is created and the ID is updated.
     *
     * @param event     The event to save.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void saveEvent(
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isValidEvent(event)) {
            onFailure.onFailure(new IllegalArgumentException("Invalid event."));
            return;
        }

        // If eventId is set, use it; otherwise create a new doc id
        if (isNonEmpty(event.getEventId())) {
            db.collection("events")
                    .document(event.getEventId())
                    .set(event)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure);
        } else {
            db.collection("events")
                    .add(event)
                    .addOnSuccessListener(ref -> {
                        // write back the generated id for consistency
                        event.setEventId(ref.getId());
                        // update stored doc to include eventId if desired
                        ref.set(event)
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(onFailure);
                    })
                    .addOnFailureListener(onFailure);
        }
    }

    /**
     * Deletes an event from Firestore by its ID.
     *
     * @param eventId   The unique identifier of the event.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void deleteEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("Event ID must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // deleteEventsByOrganizer is from Anthropic, Claude, 2026-03-14
    // Prompted to implement functionality in FirebaseRepository.java
    // such that events created by a given organizer (determined by UID)
    // are deleted.
    //
    // This is so that, when an admin removes a user, that user's events (if any exist)
    // are also removed from the database

    /**
     * Deletes all events whose organizerUid matches the given UID.
     * Each matching event document is deleted individually. The onSuccess callback
     * is invoked once after all deletions complete. If any single deletion fails,
     * onFailure is called immediately and remaining deletions are abandoned.
     *
     * @param organizerUid The UID of the organizer whose events should be deleted.
     * @param onSuccess    Callback invoked when all events have been deleted.
     * @param onFailure    Callback invoked if the query or any deletion fails.
     */
    public void deleteEventsByOrganizer(
            @NonNull String organizerUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(organizerUid)) {
            onFailure.onFailure(new IllegalArgumentException("organizerUid must not be empty."));
            return;
        }

        db.collection("events")
                .whereEqualTo("organizerUid", organizerUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        docs.add(doc);
                    }

                    if (docs.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }

                    // Delete each event document, counting completions.
                    int[] remaining = {docs.size()};
                    boolean[] failed = {false};

                    for (QueryDocumentSnapshot doc : docs) {
                        doc.getReference()
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    if (failed[0]) return;
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        onSuccess.onSuccess(null);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (!failed[0]) {
                                        failed[0] = true;
                                        onFailure.onFailure(e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Retrieves all user profiles from Firestore.
     *
     * @param onSuccess Callback receiving a list of all user profiles.
     * @param onFailure Callback for failed operation.
     */
    public void getAllUsers(
            @NonNull OnSuccessListener<List<UserProfile>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        db.collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<UserProfile> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        UserProfile user = doc.toObject(UserProfile.class);
                        users.add(user);
                    }
                    onSuccess.onSuccess(users);
                })
                .addOnFailureListener(onFailure);
    }

    // WAITING LIST SECTION:

    /**
     * Retrieves a specific waiting list entry for a user and event.
     *
     * @param eventId    The ID of the event.
     * @param entrantUid The UID of the entrant.
     * @param onSuccess  Callback receiving the WaitingListEntry (null if not found).
     * @param onFailure  Callback for failed operation.
     */
    public void getWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<WaitingListEntry> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(entrantUid)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and entrantUid must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("waiting_list")
                .document(entrantUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    WaitingListEntry entry = null;
                    if (snapshot.exists()) {
                        entry = snapshot.toObject(WaitingListEntry.class);
                    }
                    onSuccess.onSuccess(entry);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Adds or updates a waiting list entry.
     *
     * @param eventId    The ID of the event.
     * @param entrantUid The UID of the entrant.
     * @param entry      The entry data to save.
     * @param onSuccess  Callback for successful operation.
     * @param onFailure  Callback for failed operation.
     */
    public void upsertWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(entrantUid)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and entrantUid must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("waiting_list")
                .document(entrantUid)
                .set(entry)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Retrieves all waiting list entries for a given event.
     *
     * @param eventId   The ID of the event.
     * @param onSuccess Callback receiving a list of all waiting list entries.
     * @param onFailure Callback for failed operation.
     */
    public void getAllWaitingListEntries(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("waiting_list")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingListEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingListEntry entry = doc.toObject(WaitingListEntry.class);
                        entries.add(entry);
                    }
                    onSuccess.onSuccess(entries);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Updates an existing waiting list entry.
     *
     * @param eventId    The ID of the event.
     * @param entrantUid The UID of the entrant.
     * @param entry      The updated entry data.
     * @param onSuccess  Callback for successful operation.
     * @param onFailure  Callback for failed operation.
     */
    public void updateWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        upsertWaitingListEntry(eventId, entrantUid, entry, onSuccess, onFailure);
    }

    /**
     * Removes a user from an event's waiting list.
     *
     * @param eventId    The ID of the event.
     * @param entrantUid The UID of the entrant.
     * @param onSuccess  Callback for successful operation.
     * @param onFailure  Callback for failed operation.
     */
    public void deleteWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(entrantUid)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and entrantUid must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("waiting_list")
                .document(entrantUid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
// NOTIFICATIONS SECTION:

    public void saveNotification(
            @NonNull NotificationRecord record,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (record.getNotificationId() == null) {
            onFailure.onFailure(new IllegalArgumentException("notificationId must not be empty."));
            return;
        }
        db.collection("notifications")
                .document(record.getNotificationId())
                .set(record)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void saveNotificationsBatch(
            @NonNull List<NotificationRecord> records,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (records.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }

        WriteBatch batch = db.batch();
        for (NotificationRecord record : records) {
            if (record == null || !isNonEmpty(record.getNotificationId())) {
                onFailure.onFailure(new IllegalArgumentException("All notification records must have IDs."));
                return;
            }
            DocumentReference ref = db.collection("notifications")
                    .document(record.getNotificationId());
            batch.set(ref, record);
        }

        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // COMMENTS SECTION:
    public void addComment(
            @NonNull String eventId,
            @NonNull EventComment comment,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || comment == null) {
            onFailure.onFailure(new IllegalArgumentException("eventId and comment must not be empty."));
            return;
        }

        DocumentReference commentsRef = db.collection("events")
                .document(eventId)
                .collection("comments")
                .document();

        comment.setCommentId(commentsRef.getId());
        comment.setEventId(eventId);

        commentsRef.set(comment)
                .addOnSuccessListener(unused -> onSuccess.onSuccess(comment))
                .addOnFailureListener(onFailure);
    }

    public void getCommentById(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(commentId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and commentId must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(commentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    EventComment comment = null;
                    if (snapshot.exists()) {
                        comment = snapshot.toObject(EventComment.class);
                        if (comment != null && !isNonEmpty(comment.getCommentId())) {
                            comment.setCommentId(snapshot.getId());
                        }
                    }
                    onSuccess.onSuccess(comment);
                })
                .addOnFailureListener(onFailure);
    }

    public void updateComment(
            @NonNull String eventId,
            @NonNull EventComment comment,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || comment == null || !isNonEmpty(comment.getCommentId())) {
            onFailure.onFailure(new IllegalArgumentException("eventId and commentId must not be empty."));
            return;
        }
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(comment.getCommentId())
                .set(comment)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void getTopLevelCommentsOnce(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .whereEqualTo("parentCommentId", null)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<EventComment> comments = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        EventComment comment = doc.toObject(EventComment.class);
                        if (comment == null) continue;
                        if (comment.getCommentId() == null || comment.getCommentId().trim().isEmpty()) {
                            comment.setCommentId(doc.getId());
                        }
                        comments.add(comment);
                    }
                    onSuccess.onSuccess(comments);
                })
                .addOnFailureListener(onFailure);
    }

    @NonNull
    public ListenerRegistration subscribeComments(
            @NonNull String eventId,
            @Nullable String parentCommentId,
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        Query query = db.collection("events")
                .document(eventId)
                .collection("comments")
                .whereEqualTo("parentCommentId", parentCommentId);

        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                onFailure.onFailure(error);
                return;
            }

            List<EventComment> comments = new ArrayList<>();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    EventComment comment = doc.toObject(EventComment.class);
                    if (comment == null) continue;
                    if (comment.getCommentId() == null || comment.getCommentId().trim().isEmpty()) {
                        comment.setCommentId(doc.getId());
                    }
                    comments.add(comment);
                }
            }
            comments.sort((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
            onSuccess.onSuccess(comments);
        });
    }

    public void toggleCommentLike(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull String uid,
            boolean currentlyLiked,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(commentId) || !isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("eventId, commentId and uid must not be empty."));
            return;
        }

        DocumentReference commentRef = db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(commentId);
        DocumentReference likeRef = commentRef.collection("likes").document(uid);

        db.runTransaction(transaction -> {
            long now = System.currentTimeMillis();
            Long currentCount = transaction.get(commentRef).getLong("likeCount");
            long safeCount = currentCount == null ? 0L : currentCount;
            boolean isLikedInDb = transaction.get(likeRef).exists();
            boolean willLike = !isLikedInDb;

            if (isLikedInDb) {
                transaction.delete(likeRef);
                transaction.update(commentRef, "likeCount", Math.max(0L, safeCount - 1L));
            } else {
                transaction.set(likeRef, new LikeRecord(uid, now));
                transaction.update(commentRef, "likeCount", safeCount + 1L);
            }

            return willLike;
        }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void getLikeStatesForComments(
            @NonNull String eventId,
            @NonNull String uid,
            @NonNull List<String> commentIds,
            @NonNull OnSuccessListener<Set<String>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and uid must not be empty."));
            return;
        }

        if (commentIds.isEmpty()) {
            onSuccess.onSuccess(new HashSet<>());
            return;
        }

        Set<String> likedCommentIds = new HashSet<>();
        AtomicInteger remaining = new AtomicInteger(commentIds.size());
        AtomicBoolean failed = new AtomicBoolean(false);

        for (String commentId : commentIds) {
            if (!isNonEmpty(commentId)) {
                if (remaining.decrementAndGet() == 0 && !failed.get()) {
                    onSuccess.onSuccess(likedCommentIds);
                }
                continue;
            }

            db.collection("events")
                    .document(eventId)
                    .collection("comments")
                    .document(commentId)
                    .collection("likes")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            likedCommentIds.add(commentId);
                        }
                        if (remaining.decrementAndGet() == 0 && !failed.get()) {
                            onSuccess.onSuccess(likedCommentIds);
                        }
                    })
                    .addOnFailureListener(error -> {
                        if (failed.compareAndSet(false, true)) {
                            onFailure.onFailure(error);
                        }
                    });
        }
    }

    public void deleteCommentWithReplies(
            @NonNull String eventId,
            @NonNull String topLevelCommentId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(topLevelCommentId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and topLevelCommentId must not be empty."));
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("comments")
                .whereEqualTo("parentCommentId", topLevelCommentId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> commentIds = new ArrayList<>();
                    commentIds.add(topLevelCommentId);

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        commentIds.add(doc.getId());
                    }

                    deleteCommentsAndLikes(eventId, commentIds, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    public void deleteSingleCommentWithLikes(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(commentId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and commentId must not be empty."));
            return;
        }
        deleteCommentsAndLikes(eventId, java.util.Collections.singletonList(commentId), onSuccess, onFailure);
    }

    private void deleteCommentsAndLikes(
            @NonNull String eventId,
            @NonNull List<String> commentIds,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (commentIds.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(commentIds.size());
        AtomicBoolean failed = new AtomicBoolean(false);

        for (String commentId : commentIds) {
            DocumentReference commentRef = db.collection("events")
                    .document(eventId)
                    .collection("comments")
                    .document(commentId);

            commentRef.collection("likes")
                    .get()
                    .addOnSuccessListener(likesSnapshot -> {
                        WriteBatch batch = db.batch();
                        for (QueryDocumentSnapshot likeDoc : likesSnapshot) {
                            batch.delete(likeDoc.getReference());
                        }
                        batch.delete(commentRef);
                        batch.commit()
                                .addOnSuccessListener(unused -> {
                                    if (remaining.decrementAndGet() == 0 && !failed.get()) {
                                        onSuccess.onSuccess(null);
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    if (failed.compareAndSet(false, true)) {
                                        onFailure.onFailure(error);
                                    }
                                });
                    })
                    .addOnFailureListener(error -> {
                        if (failed.compareAndSet(false, true)) {
                            onFailure.onFailure(error);
                        }
                    });
        }
    }

    private static class LikeRecord {
        public String uid;
        public long likedAtMillis;

        public LikeRecord() {}

        LikeRecord(String uid, long likedAtMillis) {
            this.uid = uid;
            this.likedAtMillis = likedAtMillis;
        }
    }

    public void getNotificationsForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }
        db.collection("notifications")
                .whereEqualTo("recipientUid", uid)
                .orderBy("sentAtMillis",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<NotificationRecord> records = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        records.add(doc.toObject(NotificationRecord.class));
                    }
                    onSuccess.onSuccess(records);
                })
                .addOnFailureListener(onFailure);
    }

    public void getWaitlistEntriesForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }
        db.collectionGroup("waiting_list")
                .whereEqualTo("entrantUid", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingListEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        entries.add(doc.toObject(WaitingListEntry.class));
                    }
                    onSuccess.onSuccess(entries);
                })
                .addOnFailureListener(onFailure);
    }

    // IMAGE ASSETS SECTION

    /**
     * Retrieves all image asset metadata documents from the "images" collection.
     *
     * @param onSuccess Callback receiving the list of all ImageAssets.
     * @param onFailure Callback for failed operation.
     */
    public void getEventsWithPosters(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        getAllEvents(events -> {
            List<Event> withPosters = new ArrayList<>();
            for (Event e : events) {
                if (e.getPosterImageId() != null && !e.getPosterImageId().trim().isEmpty()) {
                    withPosters.add(e);
                }
            }
            onSuccess.onSuccess(withPosters);
        }, onFailure);
    }

    public void removeEventPoster(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        db.collection("events").document(eventId)
                .update("posterImageId", null)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // VALIDATION SECTION:
    private boolean isValidUser(@Nullable UserProfile user) {
        if (user == null) return false;
        return isNonEmpty(user.getUid())
                && isNonEmpty(user.getRole())
                && isNonEmpty(user.getInstallationId());
    }

    private boolean isValidEvent(@Nullable Event event) {
        if (event == null) return false;
        // Minimal validity for now (title + organizer + registration window)
        return isNonEmpty(event.getTitle())
                && isNonEmpty(event.getOrganizerUid())
                && event.getRegistrationOpensMillis() >= 0
                && event.getRegistrationClosesMillis() >= 0;
    }

    private boolean isNonEmpty(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Seeds a demo event into Firestore for testing purposes.
     *
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void seedDemoEvent(OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        Event demo = new Event(
                "demo_event_1",
                "Beginner Swimming Lessons",
                "One-week registration lottery. 20 spots available.",
                "Local Rec Centre",
                "123 Main St",
                1743465600000L,
                1743469200000L,
                1740787200000L,
                1741392000000L,
                20,
                20,
                200,
                false,
                "Random draw after registration closes. Declines trigger replacement draws.",
                "demo_organizer",
                null,
                null,
                null,
                false,
                false

        );

        db.collection("events").document("demo_event_1").set(demo)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Updates the notification mute status for a user.
     *
     * @param uid       The unique identifier of the user.
     * @param muted     True to mute notifications, false to enable.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void setNotificationsMuted(
            @NonNull String uid,
            boolean muted,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }
        db.collection("users")
                .document(uid)
                .update("notificationsEnabled", !muted)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}