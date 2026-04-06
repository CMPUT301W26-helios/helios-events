package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.EventRepository;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
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
 * Seeds deterministic geolocation test data so admins can verify location capture and map views
 * on a single device without needing a second session.
 */
public class AdminGeolocationTestService {

    @FunctionalInterface
    interface Clock {
        long now();
    }

    public static class SandboxState {
        private final Event event;
        private final String currentUid;
        private final String currentUserLabel;
        private final int fakeEntrantCount;
        private final int locationPointCount;
        private final String scenarioLabel;

        SandboxState(
                @NonNull Event event,
                @NonNull String currentUid,
                @NonNull String currentUserLabel,
                int fakeEntrantCount,
                int locationPointCount,
                @NonNull String scenarioLabel
        ) {
            this.event = event;
            this.currentUid = currentUid;
            this.currentUserLabel = currentUserLabel;
            this.fakeEntrantCount = fakeEntrantCount;
            this.locationPointCount = locationPointCount;
            this.scenarioLabel = scenarioLabel;
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

        public int getLocationPointCount() {
            return locationPointCount;
        }

        @NonNull
        public String getScenarioLabel() {
            return scenarioLabel;
        }
    }

    private static final String SANDBOX_EVENT_PREFIX = "dev_geolocation_sandbox_";
    private static final String SANDBOX_USER_PREFIX = "dev_geolocation_user_";
    private static final String SANDBOX_TITLE = "Developer Geolocation Sandbox";

    private final AuthDeviceService authDeviceService;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final WaitingListRepository waitingListRepository;
    private final Clock clock;

    public AdminGeolocationTestService() {
        this(new FirebaseRepository());
    }

    public AdminGeolocationTestService(@NonNull FirebaseRepository repository) {
        this(
                new AuthDeviceService(),
                repository,
                repository,
                repository,
                System::currentTimeMillis
        );
    }

    public AdminGeolocationTestService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull UserRepository userRepository,
            @NonNull EventRepository eventRepository,
            @NonNull WaitingListRepository waitingListRepository,
            @NonNull Clock clock
    ) {
        this.authDeviceService = authDeviceService;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.waitingListRepository = waitingListRepository;
        this.clock = clock;
    }

    public AdminGeolocationTestService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull UserRepository userRepository,
            @NonNull EventRepository eventRepository,
            @NonNull WaitingListRepository waitingListRepository
    ) {
        this(
                authDeviceService,
                userRepository,
                eventRepository,
                waitingListRepository,
                System::currentTimeMillis
        );
    }

    public void createOrRefreshSandbox(
            @NonNull OnSuccessListener<SandboxState> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        prepareSandbox(GeoScenario.EDMONTON_CLUSTER, onSuccess, onFailure);
    }

    public void createRegionalSpreadSandbox(
            @NonNull OnSuccessListener<SandboxState> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        prepareSandbox(GeoScenario.ALBERTA_SPREAD, onSuccess, onFailure);
    }

    private void prepareSandbox(
            @NonNull GeoScenario scenario,
            @NonNull OnSuccessListener<SandboxState> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        withCurrentUser(currentUser -> {
            Event sandboxEvent = buildSandboxEvent(currentUser.uid, clock.now(), scenario);
            eventRepository.saveEvent(
                    sandboxEvent,
                    unused -> saveFakeUsers(
                            currentUser.uid,
                            ignored -> upsertEntries(
                                    sandboxEvent.getEventId(),
                                    buildScenarioEntries(
                                            sandboxEvent.getEventId(),
                                            currentUser.uid,
                                            scenario,
                                            clock.now()
                                    ),
                                    saved -> onSuccess.onSuccess(buildSandboxState(sandboxEvent, currentUser, scenario)),
                                    onFailure
                            ),
                            onFailure
                    ),
                    onFailure
            );
        }, onFailure);
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

    private void saveFakeUsers(
            @NonNull String currentUid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        List<UserProfile> fakeUsers = buildFakeUsers(currentUid);
        if (fakeUsers.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }

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
            @NonNull GeoScenario scenario,
            long now
    ) {
        List<WaitingListEntry> entries = new ArrayList<>();
        CoordinateSeed[] coordinateSeeds = scenario.getCoordinateSeeds();
        List<String> fakeUids = buildFakeUserUids(currentUid);

        if (coordinateSeeds.length > 0) {
            entries.add(buildEntry(eventId, currentUid, coordinateSeeds[0], now));
        }

        for (int i = 1; i < coordinateSeeds.length && i - 1 < fakeUids.size(); i++) {
            entries.add(buildEntry(eventId, fakeUids.get(i - 1), coordinateSeeds[i], now));
        }
        return entries;
    }

    @NonNull
    private WaitingListEntry buildEntry(
            @NonNull String eventId,
            @NonNull String entrantUid,
            @NonNull CoordinateSeed coordinateSeed,
            long now
    ) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEventId(eventId);
        entry.setEntrantUid(entrantUid);
        entry.setStatus(WaitingListStatus.WAITING);
        entry.setJoinedAtMillis(now);
        entry.setJoinLatitude(coordinateSeed.latitude);
        entry.setJoinLongitude(coordinateSeed.longitude);
        entry.setStatusReason("Developer geolocation sandbox seed.");
        return entry;
    }

    @NonNull
    private Event buildSandboxEvent(
            @NonNull String currentUid,
            long now,
            @NonNull GeoScenario scenario
    ) {
        Event event = new Event();
        event.setEventId(buildSandboxEventId(currentUid));
        event.setTitle(SANDBOX_TITLE);
        event.setDescription("Private admin-only event used to exercise entrant geolocation flows on one device.");
        event.setLocationName("Helios Geolocation Lab");
        event.setAddress(scenario.getScenarioLabel());
        event.setStartTimeMillis(now + 86_400_000L);
        event.setEndTimeMillis(now + 90_000_000L);
        event.setRegistrationOpensMillis(Math.max(0L, now - 3_600_000L));
        event.setRegistrationClosesMillis(now + 86_400_000L);
        event.setCapacity(4);
        event.setSampleSize(2);
        event.setWaitlistLimit(12);
        event.setGeolocationRequired(true);
        event.setLotteryGuidelines("Developer geolocation sandbox. Refresh any time from Admin to reseed map coordinates.");
        event.setOrganizerUid(currentUid);
        event.setPrivateEvent(true);
        event.setDrawCount(0);
        event.setGeofenceCenter(scenario.getFenceCenter().latitude, scenario.getFenceCenter().longitude);
        event.setGeofenceRadiusMeters(scenario.getFenceRadiusMeters());
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
            user.setName("Geo Sandbox Entrant " + displayIndex);
            user.setEmail(String.format(Locale.US, "geo-sandbox-entrant-%d@helios.local", displayIndex));
            user.setPhone("555-020" + displayIndex);
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
    private SandboxState buildSandboxState(
            @NonNull Event event,
            @NonNull CurrentUserContext currentUser,
            @NonNull GeoScenario scenario
    ) {
        String label = currentUser.profile.getDisplayNameOrFallback();
        if (label == null || label.trim().isEmpty()) {
            label = currentUser.uid;
        }

        return new SandboxState(
                event,
                currentUser.uid,
                label,
                buildFakeUserUids(currentUser.uid).size(),
                scenario.getCoordinateSeeds().length,
                scenario.getScenarioLabel()
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

    private static final class CoordinateSeed {
        private final double latitude;
        private final double longitude;

        private CoordinateSeed(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private enum GeoScenario {
        EDMONTON_CLUSTER(
                "Edmonton cluster",
                new CoordinateSeed(53.5461, -113.4938),
                new CoordinateSeed(53.5498, -113.5013),
                new CoordinateSeed(53.5424, -113.4867),
                new CoordinateSeed(53.5510, -113.4789)
        ),
        ALBERTA_SPREAD(
                "Alberta spread",
                new CoordinateSeed(53.5461, -113.4938),
                new CoordinateSeed(51.0447, -114.0719),
                new CoordinateSeed(52.2681, -113.8112),
                new CoordinateSeed(53.9333, -116.5765)
        );

        private final String scenarioLabel;
        private final CoordinateSeed[] coordinateSeeds;

        GeoScenario(@NonNull String scenarioLabel, @NonNull CoordinateSeed... coordinateSeeds) {
            this.scenarioLabel = scenarioLabel;
            this.coordinateSeeds = coordinateSeeds;
        }

        @NonNull
        public String getScenarioLabel() {
            return scenarioLabel;
        }

        @NonNull
        public CoordinateSeed[] getCoordinateSeeds() {
            return coordinateSeeds;
        }

        @NonNull
        public CoordinateSeed getFenceCenter() {
            return coordinateSeeds[0];
        }

        public int getFenceRadiusMeters() {
            CoordinateSeed center = getFenceCenter();
            int radius = 0;
            for (CoordinateSeed point : coordinateSeeds) {
                radius = Math.max(radius, (int) Math.ceil(calculateDistanceMeters(
                        center.latitude,
                        center.longitude,
                        point.latitude,
                        point.longitude
                )));
            }
            return Math.max(radius + 100, 100);
        }
    }

    private static double calculateDistanceMeters(
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
}
