package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.EventRepository;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seeds deterministic notification test data for admins and drives real notification flows
 * against that sandbox using the existing repository and notification services.
 */
public class AdminNotificationTestService {

    @FunctionalInterface
    interface Clock {
        long now();
    }

    public static class SandboxState {
        private final Event event;
        private final String currentUid;
        private final String currentUserLabel;
        private final int fakeEntrantCount;
        private final boolean notificationsEnabled;
        private final boolean pushReady;

        SandboxState(
                @NonNull Event event,
                @NonNull String currentUid,
                @NonNull String currentUserLabel,
                int fakeEntrantCount,
                boolean notificationsEnabled,
                boolean pushReady
        ) {
            this.event = event;
            this.currentUid = currentUid;
            this.currentUserLabel = currentUserLabel;
            this.fakeEntrantCount = fakeEntrantCount;
            this.notificationsEnabled = notificationsEnabled;
            this.pushReady = pushReady;
        }

        @NonNull
        public Event getEvent() {
            return event;
        }

        @NonNull
        public String getCurrentUid() {
            return currentUid;
        }

        @NonNull
        public String getCurrentUserLabel() {
            return currentUserLabel;
        }

        public int getFakeEntrantCount() {
            return fakeEntrantCount;
        }

        public boolean isNotificationsEnabled() {
            return notificationsEnabled;
        }

        public boolean isPushReady() {
            return pushReady;
        }
    }

    private static final String SANDBOX_EVENT_PREFIX = "dev_notification_sandbox_";
    private static final String SANDBOX_USER_PREFIX = "dev_notification_user_";
    private static final String SANDBOX_TITLE = "Developer Notification Sandbox";

    private final AuthDeviceService authDeviceService;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final WaitingListRepository waitingListRepository;
    private final OrganizerNotificationService organizerNotificationService;
    private final Clock clock;

    public AdminNotificationTestService() {
        this(new FirebaseRepository());
    }

    public AdminNotificationTestService(@NonNull FirebaseRepository repository) {
        this(
                new AuthDeviceService(),
                repository,
                repository,
                repository,
                new OrganizerNotificationService(repository),
                System::currentTimeMillis
        );
    }

    public AdminNotificationTestService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull UserRepository userRepository,
            @NonNull EventRepository eventRepository,
            @NonNull WaitingListRepository waitingListRepository,
            @NonNull OrganizerNotificationService organizerNotificationService,
            @NonNull Clock clock
    ) {
        this.authDeviceService = authDeviceService;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.waitingListRepository = waitingListRepository;
        this.organizerNotificationService = organizerNotificationService;
        this.clock = clock;
    }

    public AdminNotificationTestService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull UserRepository userRepository,
            @NonNull EventRepository eventRepository,
            @NonNull WaitingListRepository waitingListRepository,
            @NonNull OrganizerNotificationService organizerNotificationService
    ) {
        this(
                authDeviceService,
                userRepository,
                eventRepository,
                waitingListRepository,
                organizerNotificationService,
                System::currentTimeMillis
        );
    }

    public void createOrRefreshSandbox(
            @NonNull OnSuccessListener<SandboxState> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        prepareSandbox(
                                currentUser,
                                WaitingListStatus.WAITING,
                                Arrays.asList(
                                        WaitingListStatus.WAITING,
                                        WaitingListStatus.ACCEPTED,
                                        WaitingListStatus.CANCELLED
                                ),
                                onSuccess,
                                onFailure
                        ),
                onFailure
        );
    }

    public void sendWaitingBroadcast(
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        prepareSandbox(
                                currentUser,
                                WaitingListStatus.WAITING,
                                Arrays.asList(
                                        WaitingListStatus.WAITING,
                                        WaitingListStatus.ACCEPTED,
                                        WaitingListStatus.CANCELLED
                                ),
                                sandboxState -> organizerNotificationService.sendToAudience(
                                        currentUser.uid,
                                        sandboxState.getEvent().getEventId(),
                                        NotificationAudience.WAITING,
                                        "Sandbox waiting-list update",
                                        "Developer test broadcast to waiting-list entrants.",
                                        onSuccess,
                                        onFailure
                                ),
                                onFailure
                        ),
                onFailure
        );
    }

    public void sendSelectedBroadcastToSelf(
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        prepareSandbox(
                                currentUser,
                                WaitingListStatus.INVITED,
                                Arrays.asList(
                                        WaitingListStatus.ACCEPTED,
                                        WaitingListStatus.WAITING,
                                        WaitingListStatus.CANCELLED
                                ),
                                sandboxState -> organizerNotificationService.sendToAudience(
                                        currentUser.uid,
                                        sandboxState.getEvent().getEventId(),
                                        NotificationAudience.SELECTED,
                                        "Sandbox selected-entrant update",
                                        "Developer test broadcast to selected entrants on this device.",
                                        onSuccess,
                                        onFailure
                                ),
                                onFailure
                        ),
                onFailure
        );
    }

    public void sendCancelledBroadcastToSelf(
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        prepareSandbox(
                                currentUser,
                                WaitingListStatus.CANCELLED,
                                Arrays.asList(
                                        WaitingListStatus.WAITING,
                                        WaitingListStatus.ACCEPTED,
                                        WaitingListStatus.CANCELLED
                                ),
                                sandboxState -> organizerNotificationService.sendToAudience(
                                        currentUser.uid,
                                        sandboxState.getEvent().getEventId(),
                                        NotificationAudience.CANCELLED,
                                        "Sandbox cancelled-entrant update",
                                        "Developer test broadcast to cancelled entrants on this device.",
                                        onSuccess,
                                        onFailure
                                ),
                                onFailure
                        ),
                onFailure
        );
    }

    public void simulateDrawWithAdminSelected(
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        simulateDeterministicDraw(true, onSuccess, onFailure);
    }

    public void simulateDrawWithAdminNotSelected(
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        simulateDeterministicDraw(false, onSuccess, onFailure);
    }

    public void sendPrivateInviteToSelf(
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        createOrRefreshSandboxForCurrentUser(currentUser, sandboxState ->
                                        organizerNotificationService.notifyPrivateEventInvite(
                                                currentUser.uid,
                                                sandboxState.getEvent(),
                                                currentUser.uid,
                                                onSuccess,
                                                onFailure
                                        ),
                                onFailure
                        ),
                onFailure
        );
    }

    public void sendCoOrganizerInviteToSelf(
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        createOrRefreshSandboxForCurrentUser(currentUser, sandboxState ->
                                        organizerNotificationService.notifyCoOrganizerInvite(
                                                currentUser.uid,
                                                sandboxState.getEvent(),
                                                currentUser.uid,
                                                onSuccess,
                                                onFailure
                                        ),
                                onFailure
                        ),
                onFailure
        );
    }

    private void simulateDeterministicDraw(
            boolean adminWins,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser ->
                        prepareSandbox(
                                currentUser,
                                WaitingListStatus.WAITING,
                                Arrays.asList(
                                        WaitingListStatus.WAITING,
                                        WaitingListStatus.WAITING,
                                        WaitingListStatus.WAITING
                                ),
                                sandboxState -> {
                                    Event event = sandboxState.getEvent();
                                    List<String> fakeUids = buildFakeUserUids(currentUser.uid);
                                    List<WaitingListEntry> winners = new ArrayList<>();
                                    List<WaitingListEntry> losers = new ArrayList<>();
                                    long now = clock.now();

                                    if (adminWins) {
                                        winners.add(buildEntry(event.getEventId(), currentUser.uid, WaitingListStatus.INVITED, now));
                                        winners.add(buildEntry(event.getEventId(), fakeUids.get(0), WaitingListStatus.INVITED, now));
                                        losers.add(buildEntry(event.getEventId(), fakeUids.get(1), WaitingListStatus.NOT_SELECTED, now));
                                        losers.add(buildEntry(event.getEventId(), fakeUids.get(2), WaitingListStatus.NOT_SELECTED, now));
                                    } else {
                                        winners.add(buildEntry(event.getEventId(), fakeUids.get(0), WaitingListStatus.INVITED, now));
                                        winners.add(buildEntry(event.getEventId(), fakeUids.get(1), WaitingListStatus.INVITED, now));
                                        losers.add(buildEntry(event.getEventId(), currentUser.uid, WaitingListStatus.NOT_SELECTED, now));
                                        losers.add(buildEntry(event.getEventId(), fakeUids.get(2), WaitingListStatus.NOT_SELECTED, now));
                                    }

                                    List<WaitingListEntry> persistedEntries = new ArrayList<>();
                                    persistedEntries.addAll(winners);
                                    persistedEntries.addAll(losers);

                                    upsertEntries(event.getEventId(), persistedEntries, unused -> {
                                        event.setDrawCount(event.getDrawCount() + 1);
                                        eventRepository.saveEvent(
                                                event,
                                                ignored -> organizerNotificationService.notifyDrawResults(
                                                        currentUser.uid,
                                                        event,
                                                        winners,
                                                        losers,
                                                        onSuccess,
                                                        onFailure
                                                ),
                                                onFailure
                                        );
                                    }, onFailure);
                                },
                                onFailure
                        ),
                onFailure
        );
    }

    private void withCurrentUser(
            @NonNull OnSuccessListener<CurrentUserContext> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(firebaseUser ->
                        userRepository.getUser(firebaseUser.getUid(), profile -> {
                            if (profile == null) {
                                onFailure.onFailure(new IllegalStateException("Current user profile is missing."));
                                return;
                            }
                            onSuccess.onSuccess(new CurrentUserContext(firebaseUser.getUid(), profile));
                        }, onFailure),
                onFailure
        );
    }

    private void createOrRefreshSandboxForCurrentUser(
            @NonNull CurrentUserContext currentUser,
            @NonNull OnSuccessListener<SandboxState> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        prepareSandbox(
                currentUser,
                WaitingListStatus.WAITING,
                Arrays.asList(
                        WaitingListStatus.WAITING,
                        WaitingListStatus.ACCEPTED,
                        WaitingListStatus.CANCELLED
                ),
                onSuccess,
                onFailure
        );
    }

    private void prepareSandbox(
            @NonNull CurrentUserContext currentUser,
            @NonNull WaitingListStatus currentUserStatus,
            @NonNull List<WaitingListStatus> fakeUserStatuses,
            @NonNull OnSuccessListener<SandboxState> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        Event sandboxEvent = buildSandboxEvent(currentUser.uid, clock.now());
        eventRepository.saveEvent(sandboxEvent, unused ->
                        saveFakeUsers(currentUser.uid, ignored ->
                                        upsertEntries(
                                                sandboxEvent.getEventId(),
                                                buildScenarioEntries(sandboxEvent.getEventId(), currentUser.uid, currentUserStatus, fakeUserStatuses, clock.now()),
                                                saved -> onSuccess.onSuccess(buildSandboxState(sandboxEvent, currentUser)),
                                                onFailure
                                        ),
                                onFailure
                        ),
                onFailure
        );
    }

    private void saveFakeUsers(
            @NonNull String currentUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        List<UserProfile> fakeUsers = buildFakeUsers(currentUid);
        AtomicInteger remaining = new AtomicInteger(fakeUsers.size());
        AtomicBoolean failed = new AtomicBoolean(false);

        for (UserProfile user : fakeUsers) {
            userRepository.saveUser(user, unused -> {
                if (remaining.decrementAndGet() == 0 && !failed.get()) {
                    onSuccess.onSuccess(null);
                }
            }, error -> {
                if (failed.compareAndSet(false, true)) {
                    onFailure.onFailure(error);
                }
            });
        }
    }

    private void upsertEntries(
            @NonNull String eventId,
            @NonNull List<WaitingListEntry> entries,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (entries.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(entries.size());
        AtomicBoolean failed = new AtomicBoolean(false);

        for (WaitingListEntry entry : entries) {
            waitingListRepository.upsertWaitingListEntry(
                    eventId,
                    entry.getEntrantUid(),
                    entry,
                    unused -> {
                        if (remaining.decrementAndGet() == 0 && !failed.get()) {
                            onSuccess.onSuccess(null);
                        }
                    },
                    error -> {
                        if (failed.compareAndSet(false, true)) {
                            onFailure.onFailure(error);
                        }
                    }
            );
        }
    }

    @NonNull
    private List<WaitingListEntry> buildScenarioEntries(
            @NonNull String eventId,
            @NonNull String currentUid,
            @NonNull WaitingListStatus currentUserStatus,
            @NonNull List<WaitingListStatus> fakeUserStatuses,
            long now
    ) {
        List<WaitingListEntry> entries = new ArrayList<>();
        entries.add(buildEntry(eventId, currentUid, currentUserStatus, now));

        List<String> fakeUids = buildFakeUserUids(currentUid);
        for (int i = 0; i < fakeUids.size() && i < fakeUserStatuses.size(); i++) {
            entries.add(buildEntry(eventId, fakeUids.get(i), fakeUserStatuses.get(i), now));
        }
        return entries;
    }

    @NonNull
    private WaitingListEntry buildEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull WaitingListStatus status,
            long now
    ) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEventId(eventId);
        entry.setEntrantUid(entrantUid);
        entry.setStatus(status);
        entry.setJoinedAtMillis(now);

        if (status == WaitingListStatus.INVITED || status == WaitingListStatus.ACCEPTED) {
            entry.setInvitedAtMillis(now);
        }
        if (status == WaitingListStatus.CANCELLED) {
            entry.setCancelledAtMillis(now);
            entry.setStatusReason("Developer notification sandbox cancellation.");
        }
        if (status == WaitingListStatus.NOT_SELECTED) {
            entry.setStatusReason("Developer notification sandbox draw result.");
        }
        if (status == WaitingListStatus.INVITED) {
            entry.setStatusReason("Developer notification sandbox invited entrant.");
        }
        if (status == WaitingListStatus.ACCEPTED) {
            entry.setStatusReason("Developer notification sandbox accepted entrant.");
        }
        return entry;
    }

    @NonNull
    private Event buildSandboxEvent(@NonNull String currentUid, long now) {
        Event event = new Event();
        event.setEventId(buildSandboxEventId(currentUid));
        event.setTitle(SANDBOX_TITLE);
        event.setDescription("Private admin-only event used to exercise notification flows on one device.");
        event.setLocationName("Helios Admin Lab");
        event.setAddress("Internal developer sandbox");
        event.setStartTimeMillis(now + 86_400_000L);
        event.setEndTimeMillis(now + 90_000_000L);
        event.setRegistrationOpensMillis(Math.max(0L, now - 3_600_000L));
        event.setRegistrationClosesMillis(Math.max(0L, now - 60_000L));
        event.setCapacity(2);
        event.setSampleSize(2);
        event.setWaitlistLimit(10);
        event.setGeolocationRequired(false);
        event.setLotteryGuidelines("Developer notification sandbox. Reset any time from Admin.");
        event.setOrganizerUid(currentUid);
        event.setPrivateEvent(true);
        event.setDrawCount(0);
        return event;
    }

    @NonNull
    private List<UserProfile> buildFakeUsers(@NonNull String currentUid) {
        List<String> fakeUids = buildFakeUserUids(currentUid);
        List<UserProfile> fakeUsers = new ArrayList<>();

        for (int i = 0; i < fakeUids.size(); i++) {
            UserProfile user = new UserProfile();
            String uid = fakeUids.get(i);
            int displayIndex = i + 1;
            user.setUid(uid);
            user.setName("Sandbox Entrant " + displayIndex);
            user.setEmail(String.format(Locale.US, "sandbox-entrant-%d@helios.local", displayIndex));
            user.setPhone("555-010" + displayIndex);
            user.setRole("user");
            user.setNotificationsEnabled(true);
            user.setInstallationId(uid + "_installation");
            fakeUsers.add(user);
        }

        return fakeUsers;
    }

    @NonNull
    private List<String> buildFakeUserUids(@NonNull String currentUid) {
        String safeUid = sanitizeId(currentUid);
        return Arrays.asList(
                SANDBOX_USER_PREFIX + safeUid + "_a",
                SANDBOX_USER_PREFIX + safeUid + "_b",
                SANDBOX_USER_PREFIX + safeUid + "_c"
        );
    }

    @NonNull
    private String buildSandboxEventId(@NonNull String currentUid) {
        return SANDBOX_EVENT_PREFIX + sanitizeId(currentUid);
    }

    @NonNull
    private SandboxState buildSandboxState(@NonNull Event event, @NonNull CurrentUserContext currentUser) {
        String label = currentUser.profile.getDisplayNameOrFallback();
        if (label == null || label.trim().isEmpty()) {
            label = currentUser.uid;
        }
        boolean pushReady = currentUser.profile.isNotificationsEnabled()
                && currentUser.profile.getFcmToken() != null
                && !currentUser.profile.getFcmToken().trim().isEmpty();

        return new SandboxState(
                event,
                currentUser.uid,
                label,
                buildFakeUserUids(currentUser.uid).size(),
                currentUser.profile.isNotificationsEnabled(),
                pushReady
        );
    }

    @NonNull
    private String sanitizeId(@NonNull String raw) {
        return raw.replace("/", "_");
    }

    private static class CurrentUserContext {
        private final String uid;
        private final UserProfile profile;

        CurrentUserContext(@NonNull String uid, @NonNull UserProfile profile) {
            this.uid = uid;
            this.profile = profile;
        }
    }
}
