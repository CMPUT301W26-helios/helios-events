package com.example.helios.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.Event;
import com.example.helios.model.EventComment;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.model.ImageAsset;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public class FirebaseRepository
        implements UserRepository, EventRepository, WaitingListRepository,
                   NotificationRepository, CommentRepository, ImageRepository {
    private static final String USERS_COLLECTION = "users";
    private static final String EVENTS_COLLECTION = "events";
    private static final String WAITING_LIST_COLLECTION = "waiting_list";
    private static final String COMMENTS_COLLECTION = "comments";
    private static final String NOTIFICATIONS_COLLECTION = "notifications";
    private static final String IMAGES_COLLECTION = "images";
    private static final String ADMIN_DEVICES_COLLECTION = "admin_devices";

    private final FirebaseFirestore db;

    /**
     * Initializes the FirebaseRepository with the default Firestore instance.
     */
    public FirebaseRepository() {
        this(FirebaseFirestore.getInstance());
    }

    /**
     * Initializes the FirebaseRepository with a provided Firestore instance.
     *
     * @param db The Firestore instance to use.
     */
    public FirebaseRepository(@NonNull FirebaseFirestore db) {
        this.db = db;
    }

    // USERS SECTION:

    /**
     * Saves a user profile to Firestore.
     *
     * @param user      The user profile to save.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    @Override
    public void saveUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isValidUser(user)) {
            onFailure.onFailure(new IllegalArgumentException("Invalid user profile."));
            return;
        }

        users()
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
    @Override
    public void updateUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isValidUser(user)) {
            onFailure.onFailure(new IllegalArgumentException("Invalid user profile."));
            return;
        }
        // Use merge so server-written fields (e.g. fcmToken set via saveFcmToken) are not
        // overwritten if this UserProfile object was loaded before those fields were set.
        users()
                .document(user.getUid())
                .set(user, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Retrieves a user profile from Firestore by UID.
     *
     * @param uid       The unique identifier of the user.
     * @param onSuccess Callback receiving the UserProfile (null if not found).
     * @param onFailure Callback for failed operation.
     */
    @Override
    public void getUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        users()
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    onSuccess.onSuccess(snapshot.exists()
                            ? snapshot.toObject(UserProfile.class)
                            : null);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
    public void saveFcmToken(
            @NonNull String uid,
            @Nullable String token,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("fcmToken", token);

        users()
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Deletes a user profile from Firestore by UID.
     *
     * @param uid       The unique identifier of the user.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    @Override
    public void deleteUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        users()
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
    @Override
    public void muteNotifications(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }

        users()
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
    @Override
    public void isAdminInstallation(
            @NonNull String installationId,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(installationId)) {
            onFailure.onFailure(new IllegalArgumentException("Installation ID must not be empty."));
            return;
        }

        adminDevices()
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
    @Override
    public void getAllEvents(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        // Querying all events. Filtering for 'privateEvent' should be handled by the UI
        // depending on the context (e.g., browsing vs. managing).
        events()
                .orderBy("startTimeMillis")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Event event = hydrate(doc, Event.class, new EventIdSetter());
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
    @Override
    public void getEventById(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Event> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("Event ID must not be empty."));
            return;
        }

        events()
                .document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    onSuccess.onSuccess(snapshot.exists()
                            ? hydrate(snapshot, Event.class, new EventIdSetter())
                            : null);
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
    @Override
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
            events()
                    .document(event.getEventId())
                    .set(event)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure);
        } else {
            // Generate the ID client-side so we can embed it before the single write.
            DocumentReference ref = events().document();
            event.setEventId(ref.getId());
            ref.set(event)
                    .addOnSuccessListener(onSuccess)
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
    @Override
    public void deleteEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("Event ID must not be empty."));
            return;
        }

        events()
                .document(eventId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

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
    @Override
    public void deleteEventsByOrganizer(
            @NonNull String organizerUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(organizerUid)) {
            onFailure.onFailure(new IllegalArgumentException("organizerUid must not be empty."));
            return;
        }

        events()
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
                    AtomicInteger remaining = new AtomicInteger(docs.size());
                    AtomicBoolean failed = new AtomicBoolean(false);

                    for (QueryDocumentSnapshot doc : docs) {
                        doc.getReference()
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    if (failed.get()) return;
                                    if (remaining.decrementAndGet() == 0) {
                                        onSuccess.onSuccess(null);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (failed.compareAndSet(false, true)) {
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
    @Override
    public void getAllUsers(
            @NonNull OnSuccessListener<List<UserProfile>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        users()
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
    @Override
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

        waitingList(eventId)
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
    @Override
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

        waitingList(eventId)
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
    @Override
    public void getAllWaitingListEntries(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        waitingList(eventId)
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
    @Override
    public void updateWaitingListEntry(
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
        // Use merge to avoid replacing server-added fields not present in the local object.
        waitingList(eventId)
                .document(entrantUid)
                .set(entry, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Removes a user from an event's waiting list.
     *
     * @param eventId    The ID of the event.
     * @param entrantUid The UID of the entrant.
     * @param onSuccess  Callback for successful operation.
     * @param onFailure  Callback for failed operation.
     */
    @Override
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

        waitingList(eventId)
                .document(entrantUid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
    @Override
    public void getWaitingEntriesCount(
            @NonNull String eventId,
            @NonNull WaitingListStatus status,
            @NonNull OnSuccessListener<Integer> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        waitingList(eventId)
                .whereEqualTo("status", status.name())
                .get()
                .addOnSuccessListener(querySnapshot -> onSuccess.onSuccess(querySnapshot.size()))
                .addOnFailureListener(onFailure);
    }

// NOTIFICATIONS SECTION:

    @Override
    public void saveNotification(
            @NonNull NotificationRecord record,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (record.getNotificationId() == null) {
            onFailure.onFailure(new IllegalArgumentException("notificationId must not be empty."));
            return;
        }
        notifications()
                .document(record.getNotificationId())
                .set(record)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    @Override
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
            DocumentReference ref = notifications()
                    .document(record.getNotificationId());
            batch.set(ref, record);
        }

        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // COMMENTS SECTION:
    @Override
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

        DocumentReference commentsRef = comments(eventId).document();

        comment.setCommentId(commentsRef.getId());
        comment.setEventId(eventId);

        commentsRef.set(comment)
                .addOnSuccessListener(unused -> onSuccess.onSuccess(comment))
                .addOnFailureListener(onFailure);
    }

    @Override
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

        comments(eventId)
                .document(commentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    onSuccess.onSuccess(snapshot.exists()
                            ? hydrate(snapshot, EventComment.class, new CommentIdSetter())
                            : null);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
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
        comments(eventId)
                .document(comment.getCommentId())
                .set(comment)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    @Override
    public void getTopLevelCommentsOnce(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }
        comments(eventId)
                .whereEqualTo("parentCommentId", null)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<EventComment> comments = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        EventComment comment = hydrate(doc, EventComment.class, new CommentIdSetter());
                        if (comment == null) continue;
                        comments.add(comment);
                    }
                    onSuccess.onSuccess(comments);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
    public void getAllComments(
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        // Single read using a collectionGroup query across all "comments" subcollections.
        // Requires a Firestore collectionGroup index: Firebase Console → Firestore → Indexes →
        // Collection group: "comments", exempt single-field indexes exemption is sufficient
        // for an unfiltered get(); no composite index is needed.
        db.collectionGroup(COMMENTS_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<EventComment> allComments = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        EventComment comment = hydrate(doc, EventComment.class, new CommentIdSetter());
                        if (comment != null) {
                            allComments.add(comment);
                        }
                    }
                    allComments.sort((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
                    onSuccess.onSuccess(allComments);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
    public void setPinnedComment(
            @NonNull String eventId,
            @NonNull List<EventComment> previousPinned,
            @NonNull EventComment newComment,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        DocumentReference newRef = comments(eventId).document();
        newComment.setCommentId(newRef.getId());
        newComment.setEventId(eventId);

        WriteBatch batch = db.batch();
        for (EventComment c : previousPinned) {
            if (c != null && isNonEmpty(c.getCommentId())) {
                batch.set(comments(eventId).document(c.getCommentId()), c);
            }
        }
        batch.set(newRef, newComment);

        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.onSuccess(newComment))
                .addOnFailureListener(onFailure);
    }

    @Override
    @NonNull
    public ListenerRegistration subscribeComments(
            @NonNull String eventId,
            @Nullable String parentCommentId,
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        Query query = comments(eventId)
                .whereEqualTo("parentCommentId", parentCommentId);

        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                onFailure.onFailure(error);
                return;
            }

            List<EventComment> comments = new ArrayList<>();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    EventComment comment = hydrate(doc, EventComment.class, new CommentIdSetter());
                    if (comment == null) continue;
                    comments.add(comment);
                }
            }
            comments.sort((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
            onSuccess.onSuccess(comments);
        });
    }

    @Override
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

        DocumentReference commentRef = comments(eventId).document(commentId);
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public void getNotificationsForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }
        notifications()
                .whereEqualTo("recipientUid", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<NotificationRecord> records = mapNotificationRecords(querySnapshot);
                    sortNotificationsDescending(records);
                    onSuccess.onSuccess(records);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
    @NonNull
    public ListenerRegistration subscribeNotificationsForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        Query query = notifications()
                .whereEqualTo("recipientUid", uid)
                .limit(100);

        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                onFailure.onFailure(error);
                return;
            }

            List<NotificationRecord> records = mapNotificationRecords(snapshots);
            sortNotificationsDescending(records);
            onSuccess.onSuccess(records);
        });
    }

    @Override
    public void getAllNotifications(
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        notifications()
                .orderBy("sentAtMillis",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(500)
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

    @Override
    public void deleteNotification(
            @NonNull String notificationId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(notificationId)) {
            onFailure.onFailure(new IllegalArgumentException("notificationId must not be empty."));
            return;
        }

        notifications()
                .document(notificationId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    @Override
    public void getWaitlistEntriesForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(uid)) {
            onFailure.onFailure(new IllegalArgumentException("UID must not be empty."));
            return;
        }
        // Avoid collectionGroup queries here so first-run projects do not depend on
        // manually created Firestore indexes before waiting-list reads can succeed.
        getAllEvents(events -> {
            if (events.isEmpty()) {
                onSuccess.onSuccess(new ArrayList<>());
                return;
            }

            List<WaitingListEntry> entries = new ArrayList<>();
            AtomicInteger remaining = new AtomicInteger(events.size());
            AtomicBoolean failed = new AtomicBoolean(false);

            for (Event event : events) {
                if (failed.get()) {
                    return;
                }

                String eventId = event != null ? event.getEventId() : null;
                if (!isNonEmpty(eventId)) {
                    if (remaining.decrementAndGet() == 0 && !failed.get()) {
                        sortWaitingListEntriesDescending(entries);
                        onSuccess.onSuccess(entries);
                    }
                    continue;
                }

                waitingList(eventId)
                        .document(uid)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (failed.get()) {
                                return;
                            }
                            if (snapshot.exists()) {
                                WaitingListEntry entry = snapshot.toObject(WaitingListEntry.class);
                                if (entry != null) {
                                    if (!isNonEmpty(entry.getEventId())) {
                                        entry.setEventId(eventId);
                                    }
                                    if (!isNonEmpty(entry.getEntrantUid())) {
                                        entry.setEntrantUid(uid);
                                    }
                                    entries.add(entry);
                                }
                            }
                            if (remaining.decrementAndGet() == 0) {
                                sortWaitingListEntriesDescending(entries);
                                onSuccess.onSuccess(entries);
                            }
                        })
                        .addOnFailureListener(error -> {
                            if (failed.compareAndSet(false, true)) {
                                onFailure.onFailure(error);
                            }
                        });
            }
        }, onFailure);
    }

    // IMAGE ASSETS SECTION

    /**
     * Retrieves all image asset metadata documents from the "images" collection.
     *
     * @param onSuccess Callback receiving the list of all ImageAssets.
     * @param onFailure Callback for failed operation.
     */
    @Override
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

    @Override
    public void removeEventPoster(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        events().document(eventId)
                .update("posterImageId", null)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    @Override
    public void saveImageAsset(
            @NonNull ImageAsset imageAsset,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(imageAsset.getOwnerUid())
                || !isNonEmpty(imageAsset.getEventId())
                || !isNonEmpty(imageAsset.getStoragePath())) {
            onFailure.onFailure(new IllegalArgumentException("Invalid image asset."));
            return;
        }

        if (isNonEmpty(imageAsset.getImageId())) {
            images()
                    .document(imageAsset.getImageId())
                    .set(imageAsset)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure);
            return;
        }

        images()
                .add(imageAsset)
                .addOnSuccessListener(ref -> {
                    imageAsset.setImageId(ref.getId());
                    ref.set(imageAsset)
                            .addOnSuccessListener(onSuccess)
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
    public void getImageAssetForEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<ImageAsset> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("Event ID must not be empty."));
            return;
        }

        images()
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }

                    ImageAsset latestAsset = null;
                    for (com.google.firebase.firestore.DocumentSnapshot document : querySnapshot.getDocuments()) {
                        ImageAsset candidate = hydrate(document, ImageAsset.class, new ImageIdSetter());
                        if (candidate == null) {
                            continue;
                        }
                        if (latestAsset == null
                                || candidate.getUploadedAtMillis() >= latestAsset.getUploadedAtMillis()) {
                            latestAsset = candidate;
                        }
                    }
                    onSuccess.onSuccess(latestAsset);
                })
                .addOnFailureListener(onFailure);
    }

    @Override
    public void deleteImageAsset(
            @NonNull String imageId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(imageId)) {
            onFailure.onFailure(new IllegalArgumentException("Image ID must not be empty."));
            return;
        }

        images()
                .document(imageId)
                .delete()
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
                0,
                false

        );

        events().document("demo_event_1").set(demo)
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
    @Override
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
        users()
                .document(uid)
                .update("notificationsEnabled", !muted)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    @NonNull
    private CollectionReference users() {
        return db.collection(USERS_COLLECTION);
    }

    @NonNull
    private CollectionReference events() {
        return db.collection(EVENTS_COLLECTION);
    }

    @NonNull
    private CollectionReference notifications() {
        return db.collection(NOTIFICATIONS_COLLECTION);
    }

    @NonNull
    private CollectionReference images() {
        return db.collection(IMAGES_COLLECTION);
    }

    @NonNull
    private CollectionReference adminDevices() {
        return db.collection(ADMIN_DEVICES_COLLECTION);
    }

    @NonNull
    private CollectionReference waitingList(@NonNull String eventId) {
        return events().document(eventId).collection(WAITING_LIST_COLLECTION);
    }

    @NonNull
    private CollectionReference comments(@NonNull String eventId) {
        return events().document(eventId).collection(COMMENTS_COLLECTION);
    }

    @NonNull
    private List<NotificationRecord> mapNotificationRecords(
            @Nullable Iterable<QueryDocumentSnapshot> documents
    ) {
        List<NotificationRecord> records = new ArrayList<>();
        if (documents == null) {
            return records;
        }
        for (QueryDocumentSnapshot doc : documents) {
            NotificationRecord record = doc.toObject(NotificationRecord.class);
            if (record == null) {
                continue;
            }
            if (!hasText(record.getNotificationId())) {
                record.setNotificationId(doc.getId());
            }
            records.add(record);
        }
        return records;
    }

    private void sortNotificationsDescending(@NonNull List<NotificationRecord> records) {
        records.sort((left, right) -> Long.compare(right.getSentAtMillis(), left.getSentAtMillis()));
    }

    private void sortWaitingListEntriesDescending(@NonNull List<WaitingListEntry> entries) {
        entries.sort((left, right) -> Long.compare(right.getJoinedAtMillis(), left.getJoinedAtMillis()));
    }

    @Nullable
    private <T> T hydrate(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Class<T> type,
            @NonNull IdSetter<T> idSetter
    ) {
        T value = snapshot.toObject(type);
        if (value == null) {
            return null;
        }
        idSetter.setIdIfMissing(value, snapshot.getId());
        return value;
    }

    private interface IdSetter<T> {
        void setIdIfMissing(@NonNull T value, @NonNull String id);
    }

    private static final class EventIdSetter implements IdSetter<Event> {
        @Override
        public void setIdIfMissing(@NonNull Event value, @NonNull String id) {
            if (!hasText(value.getEventId())) {
                value.setEventId(id);
            }
        }
    }

    private static final class CommentIdSetter implements IdSetter<EventComment> {
        @Override
        public void setIdIfMissing(@NonNull EventComment value, @NonNull String id) {
            if (!hasText(value.getCommentId())) {
                value.setCommentId(id);
            }
        }
    }

    private static final class ImageIdSetter implements IdSetter<ImageAsset> {
        @Override
        public void setIdIfMissing(@NonNull ImageAsset value, @NonNull String id) {
            if (!hasText(value.getImageId())) {
                value.setImageId(id);
            }
        }
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
