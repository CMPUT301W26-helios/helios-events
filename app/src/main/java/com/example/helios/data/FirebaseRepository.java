package com.example.helios.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.Event;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirebaseRepository {
    private final FirebaseFirestore db;

    public FirebaseRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // USERS SECTION:
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

    public void updateUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        saveUser(user, onSuccess, onFailure);
    }

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
    public void getAllEvents(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        db.collection("events")
                .orderBy("startTimeMillis") // matches Event.startTimeMillis
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Event event = doc.toObject(Event.class);

                        // Ensure eventId is set from document id if missing
                        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                            event.setEventId(doc.getId());
                        }

                        events.add(event);
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

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

    public void updateWaitingListEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull WaitingListEntry entry,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        upsertWaitingListEntry(eventId, entrantUid, entry, onSuccess, onFailure);
    }

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
                null
        );

        db.collection("events").document("demo_event_1").set(demo)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

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
