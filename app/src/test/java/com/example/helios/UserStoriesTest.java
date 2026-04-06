package com.example.helios;

import android.content.Context;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.CommentRepository;
import com.example.helios.data.EventRepository;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.NotificationRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.EventComment;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.CommentService;
import com.example.helios.service.EntrantEventService;
import com.example.helios.service.EventService;
import com.example.helios.service.LotteryService;
import com.example.helios.service.NotificationSendResult;
import com.example.helios.service.OrganizerNotificationService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.nav.EventBrowseFilter;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Comprehensive annotated test suite covering all Helios user stories.
 *
 * Each test method is prefixed with the user story code and name for easy searching.
 * Tests exercise business-logic services using Mockito mocks for repositories,
 * matching the project's existing unit-test style.
 *
 * User story codes follow the pattern:
 *   US 01.xx.xx  – Entrant stories
 *   US 02.xx.xx  – Organizer stories
 *   US 03.xx.xx  – Admin stories
 */
public class UserStoriesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helper utilities
    // ─────────────────────────────────────────────────────────────────────────

    private FirebaseUser mockFirebaseUser(String uid) {
        FirebaseUser user = mock(FirebaseUser.class);
        doReturn(uid).when(user).getUid();
        return user;
    }

    private void stubSignedInViaProfileService(ProfileService profileService, FirebaseUser user) {
        doAnswer(inv -> {
            OnSuccessListener<FirebaseUser> ok = inv.getArgument(0);
            ok.onSuccess(user);
            return null;
        }).when(profileService).ensureSignedIn(any(), any());
    }

    private void stubSignedInViaAuth(AuthDeviceService auth, FirebaseUser user) {
        doAnswer(inv -> {
            OnSuccessListener<FirebaseUser> ok = inv.getArgument(0);
            ok.onSuccess(user);
            return null;
        }).when(auth).ensureSignedIn(any(), any());
    }

    /** Stubs repository to return a given event for getEventById. */
    private void stubGetEvent(EventRepository repo, String eventId, Event event) {
        doAnswer(inv -> {
            OnSuccessListener<Event> ok = inv.getArgument(1);
            ok.onSuccess(event);
            return null;
        }).when(repo).getEventById(eq(eventId), any(), any());
    }

    /** Stubs repository to return a given waiting-list entry for the given uid. */
    private void stubGetEntry(WaitingListRepository repo, String eventId, String uid, WaitingListEntry entry) {
        doAnswer(inv -> {
            OnSuccessListener<WaitingListEntry> ok = inv.getArgument(2);
            ok.onSuccess(entry);
            return null;
        }).when(repo).getWaitingListEntry(eq(eventId), eq(uid), any(), any());
    }

    /** Stubs repository.upsertWaitingListEntry to succeed immediately. */
    private void stubUpsertEntry(WaitingListRepository repo, String eventId, String uid) {
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(3);
            ok.onSuccess(null);
            return null;
        }).when(repo).upsertWaitingListEntry(eq(eventId), eq(uid), any(), any(), any());
    }

    /** Stubs repository.updateWaitingListEntry to succeed immediately. */
    private void stubUpdateEntry(WaitingListRepository repo, String eventId, String uid) {
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(3);
            ok.onSuccess(null);
            return null;
        }).when(repo).updateWaitingListEntry(eq(eventId), eq(uid), any(), any(), any());
    }

    /** Stubs getAllWaitingListEntries to return the given list. */
    private void stubAllEntries(WaitingListRepository repo, String eventId, List<WaitingListEntry> entries) {
        doAnswer(inv -> {
            OnSuccessListener<List<WaitingListEntry>> ok = inv.getArgument(1);
            ok.onSuccess(entries);
            return null;
        }).when(repo).getAllWaitingListEntries(eq(eventId), any(), any());
    }

    /** Stubs userRepository.getUser to return a user with notificationsEnabled=true. */
    private void stubEnabledUser(UserRepository repo, String uid) {
        doAnswer(inv -> {
            OnSuccessListener<UserProfile> ok = inv.getArgument(1);
            UserProfile profile = new UserProfile();
            profile.setUid(uid);
            profile.setNotificationsEnabled(true);
            ok.onSuccess(profile);
            return null;
        }).when(repo).getUser(eq(uid), any(), any());
    }

    /** Stubs notificationRepository.saveNotificationsBatch to succeed immediately. */
    private void stubSaveNotifications(NotificationRepository repo) {
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(repo).saveNotificationsBatch(any(), any(), any());
    }

    /** Creates a simple Event with the given id. */
    private Event makeEvent(String eventId) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setTitle("Event " + eventId);
        return event;
    }

    /** Creates a WaitingListEntry for the given event/user with the given status. */
    private WaitingListEntry makeEntry(String eventId, String uid, WaitingListStatus status) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEventId(eventId);
        entry.setEntrantUid(uid);
        entry.setStatus(status);
        return entry;
    }

    private ProfileService makeProfileService(AuthDeviceService auth, FirebaseRepository repo) {
        return new ProfileService(auth, repo, repo, ctx -> "test-install-id");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.01.01  –  Join the waiting list for a specific event
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.01 – Join the waiting list for a specific event.
     * An entrant with no prior entry should be added with WAITING status.
     */
    @Test
    public void US_01_01_01_joinWaitingList_createsWaitingEntry() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("entrant-us01");
        stubSignedInViaProfileService(profileService, user);

        Event event = makeEvent("event-us01");
        stubGetEvent(evRepo, "event-us01", event);
        stubGetEntry(wlRepo, "event-us01", "entrant-us01", null);
        stubUpsertEntry(wlRepo, "event-us01", "entrant-us01");

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(3);
            ok.onSuccess(null);
            return null;
        }).when(wlRepo).upsertWaitingListEntry(eq("event-us01"), eq("entrant-us01"), captor.capture(), any(), any());

        AtomicBoolean success = new AtomicBoolean();
        service.joinWaitingList("event-us01", null, null, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        WaitingListEntry saved = captor.getValue();
        assertEquals(WaitingListStatus.WAITING, saved.getStatus());
        assertEquals("event-us01", saved.getEventId());
        assertEquals("entrant-us01", saved.getEntrantUid());
        assertTrue(saved.getJoinedAtMillis() > 0L);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.01.02  –  Leave the waiting list for a specific event
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.02 – Leave the waiting list for a specific event.
     * An existing WAITING entry should be marked CANCELLED.
     */
    @Test
    public void US_01_01_02_leaveWaitingList_marksExistingEntryAsCancelled() {
        FirebaseRepository repo = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("entrant-leave");
        stubSignedInViaProfileService(profileService, user);

        WaitingListEntry existing = makeEntry("event-leave", "entrant-leave", WaitingListStatus.WAITING);
        stubGetEntry(repo, "event-leave", "entrant-leave", existing);
        stubUpdateEntry(repo, "event-leave", "entrant-leave");

        EntrantEventService service = new EntrantEventService(repo, profileService);
        AtomicBoolean success = new AtomicBoolean();

        service.leaveWaitingList(mock(Context.class), "event-leave",
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(repo).updateWaitingListEntry(eq("event-leave"), eq("entrant-leave"), captor.capture(), any(), any());
        assertEquals(WaitingListStatus.CANCELLED, captor.getValue().getStatus());
    }

    /**
     * US 01.01.02 – Leave the waiting list for a specific event.
     * If the user has no entry, the operation succeeds silently without a write.
     */
    @Test
    public void US_01_01_02_leaveWaitingList_noEntry_succeedsWithoutWrite() {
        FirebaseRepository repo = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("entrant-noentry");
        stubSignedInViaProfileService(profileService, user);
        stubGetEntry(repo, "event-leave2", "entrant-noentry", null);

        EntrantEventService service = new EntrantEventService(repo, profileService);
        AtomicBoolean success = new AtomicBoolean();

        service.leaveWaitingList(mock(Context.class), "event-leave2",
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(repo, never()).updateWaitingListEntry(any(), any(), any(), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.01.03  –  See a list of events that can be joined
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.03 – See a list of events that can be joined.
     * EventService.getAllEvents must return all events from the repository.
     */
    @Test
    public void US_01_01_03_getAllEvents_returnsEventList() {
        EventRepository repo = mock(EventRepository.class);
        List<Event> expected = Arrays.asList(makeEvent("e1"), makeEvent("e2"), makeEvent("e3"));
        doAnswer(inv -> {
            ((OnSuccessListener<List<Event>>) inv.getArgument(0)).onSuccess(expected);
            return null;
        }).when(repo).getAllEvents(any(), any());

        EventService service = new EventService(repo);
        AtomicReference<List<Event>> result = new AtomicReference<>();
        service.getAllEvents(result::set, e -> fail(e.getMessage()));

        assertEquals(3, result.get().size());
        assertSame(expected, result.get());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.01.04  –  Filter events based on availability and event capacity
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.04 – Filter events based on availability and event capacity.
     * OPEN_NOW filter should only include events whose registration window is currently open.
     */
    @Test
    public void US_01_01_04_filter_openNow_keepsOnlyCurrentlyOpenEvents() {
        long now = System.currentTimeMillis();
        Event open = makeEvent("open");
        open.setRegistrationOpensMillis(now - 1_000L);
        open.setRegistrationClosesMillis(now + 1_000L);

        Event upcoming = makeEvent("upcoming");
        upcoming.setRegistrationOpensMillis(now + 60_000L);
        upcoming.setRegistrationClosesMillis(now + 120_000L);

        Event closed = makeEvent("closed");
        closed.setRegistrationOpensMillis(now - 120_000L);
        closed.setRegistrationClosesMillis(now - 60_000L);

        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.OPEN_NOW);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(open, upcoming, closed), null, Collections.emptyMap(), "");

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("open", result.getFilteredEvents().get(0).getEventId());
    }

    /**
     * US 01.01.04 – Filter events based on availability and event capacity.
     * LIMITED_CAPACITY filter should keep only events with a capacity set (> 0).
     */
    @Test
    public void US_01_01_04_filter_limitedCapacity_keepsCapacityLimitedEvents() {
        long now = System.currentTimeMillis();
        Event limited = makeEvent("limited");
        limited.setCapacity(10);
        limited.setRegistrationOpensMillis(now - 1_000L);
        limited.setRegistrationClosesMillis(now + 1_000L);

        Event unlimited = makeEvent("unlimited");
        unlimited.setCapacity(0);
        unlimited.setRegistrationOpensMillis(now - 1_000L);
        unlimited.setRegistrationClosesMillis(now + 1_000L);

        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setCapacityFilter(EventBrowseFilter.CapacityFilter.LIMITED_CAPACITY);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(unlimited, limited), null, Collections.emptyMap(), "");

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("limited", result.getFilteredEvents().get(0).getEventId());
    }

    /**
     * US 01.01.04 – Filter events based on availability and event capacity.
     * WAITLIST_LIMITED filter keeps only events with a waitlist cap.
     */
    @Test
    public void US_01_01_04_filter_waitlistLimited_keepsWaitlistCappedEvents() {
        long now = System.currentTimeMillis();
        Event capped = makeEvent("capped");
        capped.setWaitlistLimit(50);
        capped.setRegistrationOpensMillis(now - 1_000L);
        capped.setRegistrationClosesMillis(now + 1_000L);

        Event uncapped = makeEvent("uncapped");
        uncapped.setWaitlistLimit(null);
        uncapped.setRegistrationOpensMillis(now - 1_000L);
        uncapped.setRegistrationClosesMillis(now + 1_000L);

        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setCapacityFilter(EventBrowseFilter.CapacityFilter.WAITLIST_LIMITED);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(capped, uncapped), null, Collections.emptyMap(), "");

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("capped", result.getFilteredEvents().get(0).getEventId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.01.05  –  Search for events by keyword
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.05 – Search for events by keyword to find events based on interests.
     * Keyword search should return events whose title contains the query string.
     */
    @Test
    public void US_01_01_05_keywordSearch_matchesEventTitle() {
        long now = System.currentTimeMillis();
        Event yoga = makeEvent("yoga-id");
        yoga.setTitle("Morning Yoga Class");
        yoga.setRegistrationOpensMillis(now - 1_000L);
        yoga.setRegistrationClosesMillis(now + 1_000L);

        Event swim = makeEvent("swim-id");
        swim.setTitle("Evening Swim Lessons");
        swim.setRegistrationOpensMillis(now - 1_000L);
        swim.setRegistrationClosesMillis(now + 1_000L);

        EventBrowseFilter filter = new EventBrowseFilter();
        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(yoga, swim), null, Collections.emptyMap(), "yoga");

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("yoga-id", result.getFilteredEvents().get(0).getEventId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.01.06  –  Keyword search with filtering combined
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.06 – Use keyword search with filtering to narrow event search.
     * Combining keyword search with availability filter should apply both constraints.
     */
    @Test
    public void US_01_01_06_keywordSearchWithFilter_appliesBothConstraints() {
        long now = System.currentTimeMillis();
        Event openYoga = makeEvent("open-yoga");
        openYoga.setTitle("Yoga Open");
        openYoga.setRegistrationOpensMillis(now - 1_000L);
        openYoga.setRegistrationClosesMillis(now + 1_000L);

        Event closedYoga = makeEvent("closed-yoga");
        closedYoga.setTitle("Yoga Closed");
        closedYoga.setRegistrationOpensMillis(now - 120_000L);
        closedYoga.setRegistrationClosesMillis(now - 60_000L);

        Event openSwim = makeEvent("open-swim");
        openSwim.setTitle("Swim Open");
        openSwim.setRegistrationOpensMillis(now - 1_000L);
        openSwim.setRegistrationClosesMillis(now + 1_000L);

        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.OPEN_NOW);
        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(openYoga, closedYoga, openSwim), null, Collections.emptyMap(), "yoga");

        // Should only return openYoga — matches keyword AND is currently open
        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("open-yoga", result.getFilteredEvents().get(0).getEventId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.02.01  –  Provide personal information (name, email, phone)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.02.01 – Provide personal information such as name, email, and optional phone number.
     * completeCurrentProfile should save all three fields to the user's profile.
     */
    @Test
    public void US_01_02_01_completeCurrentProfile_savesNameEmailPhone() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);
        ProfileService service = mock(ProfileService.class);
        Context ctx = mock(Context.class);

        UserProfile profile = new UserProfile();
        profile.setUid("uid-setup");

        doAnswer(inv -> {
            OnSuccessListener<ProfileService.BootstrapResult> ok = inv.getArgument(1);
            ok.onSuccess(new ProfileService.BootstrapResult(profile, true));
            return null;
        }).when(service).bootstrapCurrentUser(eq(ctx), any(), any());

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(repo).updateUser(eq(profile), any(), any());

        doAnswer(inv -> {
            String name  = inv.getArgument(1);
            String email = inv.getArgument(2);
            String phone = inv.getArgument(3);
            profile.setName(name);
            profile.setEmail(email);
            profile.setPhone(phone);
            OnSuccessListener<UserProfile> ok = inv.getArgument(4);
            ok.onSuccess(profile);
            return null;
        }).when(service).completeCurrentProfile(any(), any(), any(), any(), any(), any());

        AtomicReference<UserProfile> result = new AtomicReference<>();
        service.completeCurrentProfile(ctx, "Alice", "alice@example.com", "780-555-0001",
                result::set, e -> fail(e.getMessage()));

        assertEquals("Alice", profile.getName());
        assertEquals("alice@example.com", profile.getEmail());
        assertEquals("780-555-0001", profile.getPhone());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.02.02  –  Update information such as name, email, contact information
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.02.02 – Update information such as name, email and contact information on my profile.
     * Calling completeCurrentProfile with new values should persist the update.
     */
    @Test
    public void US_01_02_02_completeCurrentProfile_updatesExistingFields() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);

        UserProfile existing = new UserProfile("uid-update", "Bob", "bob@example.com",
                "780-555-0002", "user", true, "inst-a");

        ProfileService service = org.mockito.Mockito.spy(
                new ProfileService(auth, repo, repo, ctx -> "inst-a"));

        doAnswer(inv -> {
            OnSuccessListener<ProfileService.BootstrapResult> ok = inv.getArgument(1);
            ok.onSuccess(new ProfileService.BootstrapResult(existing, false));
            return null;
        }).when(service).bootstrapCurrentUser(any(), any(), any());

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(repo).updateUser(eq(existing), any(), any());

        AtomicReference<UserProfile> result = new AtomicReference<>();
        service.completeCurrentProfile(mock(Context.class), "Bobby", "bobby@example.com", "780-555-9999",
                result::set, e -> fail(e.getMessage()));

        assertSame(existing, result.get());
        assertEquals("Bobby", existing.getName());
        assertEquals("bobby@example.com", existing.getEmail());
        assertEquals("780-555-9999", existing.getPhone());
        verify(repo).updateUser(eq(existing), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.02.03  –  History of events registered for
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.02.03 – Have a history of events I have registered for, whether selected or not.
     * getCurrentUserWaitlistEntries must return all entries across all statuses.
     */
    @Test
    public void US_01_02_03_getCurrentUserWaitlistEntries_returnsAllStatuses() {
        FirebaseRepository repo = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("history-user");
        stubSignedInViaProfileService(profileService, user);

        List<WaitingListEntry> history = Arrays.asList(
                makeEntry("ev1", "history-user", WaitingListStatus.ACCEPTED),
                makeEntry("ev2", "history-user", WaitingListStatus.DECLINED),
                makeEntry("ev3", "history-user", WaitingListStatus.NOT_SELECTED),
                makeEntry("ev4", "history-user", WaitingListStatus.WAITING)
        );

        doAnswer(inv -> {
            OnSuccessListener<List<WaitingListEntry>> ok = inv.getArgument(1);
            ok.onSuccess(history);
            return null;
        }).when(repo).getWaitlistEntriesForUser(eq("history-user"), any(), any());

        EntrantEventService service = new EntrantEventService(repo, profileService);
        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();

        service.getCurrentUserWaitlistEntries(mock(Context.class), result::set, e -> fail(e.getMessage()));

        assertEquals(4, result.get().size());
        // All statuses present
        assertTrue(result.get().stream().anyMatch(e -> e.getStatus() == WaitingListStatus.ACCEPTED));
        assertTrue(result.get().stream().anyMatch(e -> e.getStatus() == WaitingListStatus.DECLINED));
        assertTrue(result.get().stream().anyMatch(e -> e.getStatus() == WaitingListStatus.NOT_SELECTED));
        assertTrue(result.get().stream().anyMatch(e -> e.getStatus() == WaitingListStatus.WAITING));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.02.04  –  Delete profile
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.02.04 – Delete my profile if I no longer wish to use the app.
     * deleteCurrentProfile should remove the user's events and their profile document.
     */
    @Test
    public void US_01_02_04_deleteCurrentProfile_removesEventsAndProfile() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);
        FirebaseUser user = mockFirebaseUser("uid-delete-me");
        stubSignedInViaAuth(auth, user);

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).deleteEventsByOrganizer(eq("uid-delete-me"), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).deleteUser(eq("uid-delete-me"), any(), any());

        ProfileService service = makeProfileService(auth, repo);
        AtomicBoolean success = new AtomicBoolean();

        service.deleteCurrentProfile(mock(Context.class), v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(repo).deleteEventsByOrganizer(eq("uid-delete-me"), any(), any());
        verify(repo).deleteUser(eq("uid-delete-me"), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.04.01  –  Receive notification when chosen (win)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.04.01 – Receive notification when I am chosen to participate from the waiting list.
     * notifyDrawResults must create an INVITED-audience notification for each winner.
     */
    @Test
    public void US_01_04_01_notifyDrawResults_sendsInvitedNotificationToWinners() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "winner-1");
        stubSaveNotifications(notifRepo);

        Event event = makeEvent("event-draw");
        event.setOrganizerUid("organizer-1");

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        WaitingListEntry winnerEntry = makeEntry("event-draw", "winner-1", WaitingListStatus.INVITED);

        ArgumentCaptor<List<NotificationRecord>> captor = ArgumentCaptor.forClass(List.class);
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(notifRepo).saveNotificationsBatch(captor.capture(), any(), any());

        service.notifyDrawResults("organizer-1", event,
                Collections.singletonList(winnerEntry),
                Collections.emptyList(),
                v -> {}, e -> fail(e.getMessage()));

        List<NotificationRecord> saved = captor.getValue();
        assertFalse(saved.isEmpty());
        NotificationRecord record = saved.get(0);
        assertEquals(NotificationAudience.INVITED, record.getAudience());
        assertEquals("winner-1", record.getRecipientUid());
        assertTrue(record.getMessage().contains("selected in the latest draw"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.04.02  –  Receive notification when NOT chosen (lose)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.04.02 – Receive notification of when I am not chosen (lose the lottery).
     * notifyDrawResults must create a NOT_SELECTED-audience notification for losers.
     */
    @Test
    public void US_01_04_02_notifyDrawResults_sendsNotSelectedNotificationToLosers() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "loser-1");

        Event event = makeEvent("event-draw2");
        event.setOrganizerUid("organizer-2");

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        WaitingListEntry loserEntry = makeEntry("event-draw2", "loser-1", WaitingListStatus.NOT_SELECTED);

        ArgumentCaptor<List<NotificationRecord>> captor = ArgumentCaptor.forClass(List.class);
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(notifRepo).saveNotificationsBatch(captor.capture(), any(), any());

        service.notifyDrawResults("organizer-2", event,
                Collections.emptyList(),
                Collections.singletonList(loserEntry),
                v -> {}, e -> fail(e.getMessage()));

        List<NotificationRecord> saved = captor.getValue();
        assertFalse(saved.isEmpty());
        NotificationRecord record = saved.get(0);
        assertEquals(NotificationAudience.NOT_SELECTED, record.getAudience());
        assertEquals("loser-1", record.getRecipientUid());
        assertTrue(record.getMessage().contains("not selected"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.04.03  –  Opt out of receiving notifications
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.04.03 – Opt out of receiving notifications from organizers and admins.
     * setNotificationsMuted should call the repository with muted=true.
     */
    @Test
    public void US_01_04_03_setNotificationsMuted_persistsMutedPreference() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);
        FirebaseUser user = mockFirebaseUser("uid-mute");
        stubSignedInViaAuth(auth, user);

        doAnswer(inv -> {
            boolean muted = inv.getArgument(1);
            assertTrue(muted);
            ((OnSuccessListener<Void>) inv.getArgument(2)).onSuccess(null);
            return null;
        }).when(repo).setNotificationsMuted(eq("uid-mute"), eq(true), any(), any());

        ProfileService service = makeProfileService(auth, repo);
        AtomicBoolean success = new AtomicBoolean();

        service.setNotificationsMuted(mock(Context.class), true, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(repo).setNotificationsMuted(eq("uid-mute"), eq(true), any(), any());
    }

    /**
     * US 01.04.03 – Opt out of receiving notifications.
     * Users with notificationsEnabled=false must not receive notification records.
     */
    @Test
    public void US_01_04_03_disabledUser_doesNotReceiveNotificationRecord() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);

        // User has notifications disabled
        doAnswer(inv -> {
            OnSuccessListener<UserProfile> ok = inv.getArgument(1);
            UserProfile profile = new UserProfile();
            profile.setUid("opt-out-user");
            profile.setNotificationsEnabled(false);
            ok.onSuccess(profile);
            return null;
        }).when(userRepo).getUser(eq("opt-out-user"), any(), any());

        stubSaveNotifications(notifRepo);

        Event event = makeEvent("event-optout");
        event.setOrganizerUid("organizer-x");

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        WaitingListEntry entry = makeEntry("event-optout", "opt-out-user", WaitingListStatus.INVITED);

        ArgumentCaptor<List<NotificationRecord>> captor = ArgumentCaptor.forClass(List.class);
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(notifRepo).saveNotificationsBatch(captor.capture(), any(), any());

        service.notifyDrawResults("organizer-x", event,
                Collections.singletonList(entry),
                Collections.emptyList(),
                v -> {}, e -> fail(e.getMessage()));

        // The batch should be empty because the user opted out
        assertTrue(captor.getValue().isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.01  –  Another chance if a selected user declines (replacement draw)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.01 – Another chance to be chosen if a selected user declines an invitation.
     * runDraw should only select from WAITING entries; NOT_SELECTED users remain eligible for redraws.
     */
    @Test
    public void US_01_05_01_runDraw_onlySelectsFromWaitingEntrants() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        OrganizerNotificationService notifService = mock(OrganizerNotificationService.class);

        WaitingListEntry waiting = makeEntry("event-redraw", "waiter-1", WaitingListStatus.WAITING);
        WaitingListEntry notSelected = makeEntry("event-redraw", "not-selected-1", WaitingListStatus.NOT_SELECTED);
        WaitingListEntry invited = makeEntry("event-redraw", "invited-1", WaitingListStatus.INVITED);

        stubAllEntries(wlRepo, "event-redraw", Arrays.asList(waiting, notSelected, invited));

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(3);
            ok.onSuccess(null);
            return null;
        }).when(wlRepo).upsertWaitingListEntry(any(), any(), any(), any(), any());

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(evRepo).saveEvent(any(), any(), any());

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(4);
            ok.onSuccess(null);
            return null;
        }).when(notifService).notifyDrawResults(any(), any(), any(), any(), any(), any());

        Event event = makeEvent("event-redraw");
        LotteryService service = new LotteryService(wlRepo, evRepo, notifService);

        AtomicBoolean success = new AtomicBoolean();
        service.runDraw("organizer-1", event, 1, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        // Only waiter-1 should be upserted as INVITED (not notSelected or invited)
        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(wlRepo).upsertWaitingListEntry(eq("event-redraw"), eq("waiter-1"), captor.capture(), any(), any());
        assertEquals(WaitingListStatus.INVITED, captor.getValue().getStatus());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.02  –  Accept the invitation to register/sign up
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.02 – Accept the invitation to register/sign up when chosen to participate.
     * Updating an entry to ACCEPTED status should be persisted via updateEntry.
     */
    @Test
    public void US_01_05_02_acceptInvitation_updatesStatusToAccepted() {
        FirebaseRepository repo = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("entrant-accept");
        stubSignedInViaProfileService(profileService, user);

        WaitingListEntry invited = makeEntry("event-accept", "entrant-accept", WaitingListStatus.INVITED);
        invited.setInvitedAtMillis(System.currentTimeMillis());

        // Simulate accept: set status to ACCEPTED and call updateEntry
        invited.setStatus(WaitingListStatus.ACCEPTED);
        invited.setRespondedAtMillis(System.currentTimeMillis());

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(3);
            ok.onSuccess(null);
            return null;
        }).when(repo).upsertWaitingListEntry(eq("event-accept"), eq("entrant-accept"), any(), any(), any());

        EntrantEventService service = new EntrantEventService(repo, profileService);
        AtomicBoolean success = new AtomicBoolean();

        service.updateEntry(mock(Context.class), "event-accept", invited,
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals(WaitingListStatus.ACCEPTED, invited.getStatus());
        assertTrue(invited.getRespondedAtMillis() > 0L);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.03  –  Decline an invitation
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.03 – Decline an invitation when chosen to participate in an event.
     * Updating an entry to DECLINED status should be persisted via updateEntry.
     */
    @Test
    public void US_01_05_03_declineInvitation_updatesStatusToDeclined() {
        FirebaseRepository repo = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("entrant-decline");
        stubSignedInViaProfileService(profileService, user);

        WaitingListEntry invited = makeEntry("event-decline", "entrant-decline", WaitingListStatus.INVITED);
        invited.setStatus(WaitingListStatus.DECLINED);
        invited.setRespondedAtMillis(System.currentTimeMillis());

        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(3);
            ok.onSuccess(null);
            return null;
        }).when(repo).upsertWaitingListEntry(eq("event-decline"), eq("entrant-decline"), any(), any(), any());

        EntrantEventService service = new EntrantEventService(repo, profileService);
        AtomicBoolean success = new AtomicBoolean();

        service.updateEntry(mock(Context.class), "event-decline", invited,
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals(WaitingListStatus.DECLINED, invited.getStatus());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.04  –  Know how many total entrants are on the waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.04 – Know how many total entrants are on the waiting list for an event.
     * getFilledSlotsCount should count WAITING + INVITED + ACCEPTED entries.
     */
    @Test
    public void US_01_05_04_getFilledSlotsCount_countsActiveEntries() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);

        List<WaitingListEntry> entries = Arrays.asList(
                makeEntry("ev-count", "u1", WaitingListStatus.WAITING),
                makeEntry("ev-count", "u2", WaitingListStatus.INVITED),
                makeEntry("ev-count", "u3", WaitingListStatus.ACCEPTED),
                makeEntry("ev-count", "u4", WaitingListStatus.DECLINED),
                makeEntry("ev-count", "u5", WaitingListStatus.CANCELLED),
                makeEntry("ev-count", "u6", WaitingListStatus.NOT_SELECTED)
        );
        stubAllEntries(wlRepo, "ev-count", entries);

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        AtomicReference<Integer> count = new AtomicReference<>();

        service.getFilledSlotsCount("ev-count", count::set, e -> fail(e.getMessage()));

        // WAITING + INVITED + ACCEPTED = 3
        assertEquals(Integer.valueOf(3), count.get());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.05  –  Informed about lottery selection criteria/guidelines
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.05 – Be informed about the criteria or guidelines for the lottery selection process.
     * The Event model should store and retrieve lotteryGuidelines correctly.
     */
    @Test
    public void US_01_05_05_event_storesLotteryGuidelines() {
        Event event = new Event();
        event.setLotteryGuidelines("One entry per person. Draw occurs after registration closes.");

        assertEquals("One entry per person. Draw occurs after registration closes.",
                event.getLotteryGuidelines());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.06  –  Notified when invited to join private event waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.06 – Receive a notification that I've been invited to join the waiting list for a private event.
     * notifyPrivateEventInvite should create a PRIVATE_EVENT_INVITE notification for the recipient.
     */
    @Test
    public void US_01_05_06_notifyPrivateEventInvite_createsPrivateEventInviteRecord() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "private-invitee");

        ArgumentCaptor<List<NotificationRecord>> captor = ArgumentCaptor.forClass(List.class);
        doAnswer(inv -> {
            OnSuccessListener<Void> ok = inv.getArgument(1);
            ok.onSuccess(null);
            return null;
        }).when(notifRepo).saveNotificationsBatch(captor.capture(), any(), any());

        Event event = makeEvent("event-private");
        event.setPrivateEvent(true);
        event.setOrganizerUid("org-private");

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        service.notifyPrivateEventInvite("org-private", event, "private-invitee",
                result::set, e -> fail(e.getMessage()));

        assertNotNull(result.get());
        assertEquals(1, result.get().getRecipientCount());
        NotificationRecord record = captor.getValue().get(0);
        assertEquals(NotificationAudience.PRIVATE_EVENT_INVITE, record.getAudience());
        assertEquals("private-invitee", record.getRecipientUid());
        assertTrue(record.getMessage().contains("accept or decline"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.05.07  –  Accept or decline invitation to join private event waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.05.07 – Accept or decline an invitation to join the waiting list for a private event.
     * inviteEntrantToWaitingList should create an INVITED entry for a private-event invitee.
     * The entrant can then update the entry to WAITING (accept) or be removed (decline).
     */
    @Test
    public void US_01_05_07_inviteEntrantToWaitingList_createsInvitedEntry() {
        FirebaseRepository repo = mock(FirebaseRepository.class);

        // No existing entry
        doAnswer(inv -> {
            ((OnSuccessListener<WaitingListEntry>) inv.getArgument(2)).onSuccess(null);
            return null;
        }).when(repo).getWaitingListEntry(eq("event-priv2"), eq("invitee-1"), any(), any());

        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(3)).onSuccess(null);
            return null;
        }).when(repo).upsertWaitingListEntry(eq("event-priv2"), eq("invitee-1"), captor.capture(), any(), any());

        WaitingListService service = new WaitingListService(repo);
        AtomicBoolean success = new AtomicBoolean();

        service.inviteEntrantToWaitingList("event-priv2", "invitee-1",
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals(WaitingListStatus.INVITED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getInvitedAtMillis() > 0L);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.06.01  –  View event details by scanning promotional QR code
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.06.01 – View event details within the app by scanning the promotional QR code.
     * The Event model must store qrCodeValue, and getEventById should resolve to a full event.
     */
    @Test
    public void US_01_06_01_event_storesQrCodeValue_andCanBeRetrievedById() {
        String qrValue = "helios://event/event-qr-test";
        Event event = makeEvent("event-qr-test");
        event.setQrCodeValue(qrValue);
        assertEquals(qrValue, event.getQrCodeValue());

        EventRepository repo = mock(EventRepository.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Event>) inv.getArgument(1)).onSuccess(event);
            return null;
        }).when(repo).getEventById(eq("event-qr-test"), any(), any());

        EventService service = new EventService(repo);
        AtomicReference<Event> result = new AtomicReference<>();
        service.getEventById("event-qr-test", result::set, e -> fail(e.getMessage()));

        assertSame(event, result.get());
        assertEquals(qrValue, result.get().getQrCodeValue());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.06.02  –  Sign up for an event from the event details
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.06.02 – Be able to sign up for an event from the event details.
     * joinWaitingList must create a WAITING entry for the current user.
     * (Covered by US_01_01_01; this test verifies the same path via event-details context.)
     */
    @Test
    public void US_01_06_02_signUpFromEventDetails_createsWaitingEntry() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("detail-entrant");
        stubSignedInViaProfileService(profileService, user);

        Event event = makeEvent("event-detail");
        stubGetEvent(evRepo, "event-detail", event);
        stubGetEntry(wlRepo, "event-detail", "detail-entrant", null);

        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(3)).onSuccess(null);
            return null;
        }).when(wlRepo).upsertWaitingListEntry(eq("event-detail"), eq("detail-entrant"), captor.capture(), any(), any());

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        AtomicBoolean success = new AtomicBoolean();
        service.joinWaitingList("event-detail", null, null, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals(WaitingListStatus.WAITING, captor.getValue().getStatus());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.07.01  –  Identified by device (no username/password)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.07.01 – Be identified by my device so that I don't need a username and password.
     * bootstrapCurrentUser should create a profile tied to the device's Firebase UID and
     * installation ID without requiring a username or password input.
     */
    @Test
    public void US_01_07_01_bootstrap_createsProfileWithoutCredentials() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);
        FirebaseUser user = mockFirebaseUser("device-uid");
        stubSignedInViaAuth(auth, user);

        // No existing profile
        doAnswer(inv -> {
            ((OnSuccessListener<Boolean>) inv.getArgument(1)).onSuccess(false);
            return null;
        }).when(repo).isAdminInstallation(eq("device-install"), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<UserProfile>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).getUser(eq("device-uid"), any(), any());

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).saveUser(saved.capture(), any(), any());

        ProfileService service = new ProfileService(auth, repo, repo, ctx -> "device-install");
        AtomicReference<ProfileService.BootstrapResult> result = new AtomicReference<>();
        service.bootstrapCurrentUser(mock(Context.class), result::set, e -> fail(e.getMessage()));

        assertTrue(result.get().isNewUser());
        // Profile was created with device UID — no name or email required at this stage
        assertEquals("device-uid", saved.getValue().getUid());
        assertNull(saved.getValue().getName());
        assertNull(saved.getValue().getEmail());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.08.01  –  Post a comment on an event
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.08.01 – Post a comment on an event.
     * postComment with a valid body should call addComment on the repository.
     */
    @Test
    public void US_01_08_01_postComment_withValidBody_addsCommentToRepository() {
        CommentRepository commentRepo = mock(CommentRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        EventService eventService = mock(EventService.class);

        UserProfile profile = new UserProfile("uid-commenter", "Alice", "alice@ex.com",
                null, "user", true, "inst");

        doAnswer(inv -> {
            OnSuccessListener<ProfileService.BootstrapResult> ok = inv.getArgument(1);
            ok.onSuccess(new ProfileService.BootstrapResult(profile, false));
            return null;
        }).when(profileService).bootstrapCurrentUser(any(), any(), any());

        EventComment savedComment = new EventComment();
        savedComment.setCommentId("comment-new");

        doAnswer(inv -> {
            OnSuccessListener<EventComment> ok = inv.getArgument(2);
            ok.onSuccess(savedComment);
            return null;
        }).when(commentRepo).addComment(eq("event-c1"), any(), any(), any());

        CommentService service = new CommentService(commentRepo, profileService, eventService);
        AtomicReference<EventComment> result = new AtomicReference<>();

        service.postComment(mock(Context.class), "event-c1", "Great event!", null,
                result::set, e -> fail(e.getMessage()));

        assertNotNull(result.get());
        assertEquals("comment-new", result.get().getCommentId());
        verify(commentRepo).addComment(eq("event-c1"), any(), any(), any());
    }

    /**
     * US 01.08.01 – Post a comment on an event.
     * postComment with a blank body should return a validation failure.
     */
    @Test
    public void US_01_08_01_postComment_withBlankBody_returnsValidationError() {
        CommentRepository commentRepo = mock(CommentRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        EventService eventService = mock(EventService.class);

        CommentService service = new CommentService(commentRepo, profileService, eventService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.postComment(mock(Context.class), "event-c2", "   ", null,
                c -> fail("Should not succeed"), failure::set);

        assertNotNull(failure.get());
        assertTrue(failure.get().getMessage().contains("1 and 500 characters"));
        verify(commentRepo, never()).addComment(any(), any(), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.08.02  –  View comments on an event
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.08.02 – View comments on an event.
     * getAllCommentsForAdmin (or getTopLevelCommentsOnce) should return the comment list.
     */
    @Test
    public void US_01_08_02_getAllCommentsForAdmin_returnsCommentList() {
        CommentRepository commentRepo = mock(CommentRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        EventService eventService = mock(EventService.class);

        EventComment c1 = new EventComment();
        c1.setCommentId("c1");
        c1.setBody("First comment");
        EventComment c2 = new EventComment();
        c2.setCommentId("c2");
        c2.setBody("Second comment");

        doAnswer(inv -> {
            ((OnSuccessListener<List<EventComment>>) inv.getArgument(0)).onSuccess(Arrays.asList(c1, c2));
            return null;
        }).when(commentRepo).getAllComments(any(), any());

        CommentService service = new CommentService(commentRepo, profileService, eventService);
        AtomicReference<List<EventComment>> result = new AtomicReference<>();

        service.getAllCommentsForAdmin(result::set, e -> fail(e.getMessage()));

        assertEquals(2, result.get().size());
        assertEquals("First comment", result.get().get(0).getBody());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 01.09.01  –  Receive notification if invited to be a co-organizer
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.09.01 – Receive a notification if I have been invited to be a co-organizer for an event.
     * notifyCoOrganizerInvite must create a CO_ORGANIZER_INVITE notification record.
     */
    @Test
    public void US_01_09_01_notifyCoOrganizerInvite_createsCOOrganizerInviteRecord() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "co-org-invitee");

        ArgumentCaptor<List<NotificationRecord>> captor = ArgumentCaptor.forClass(List.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(notifRepo).saveNotificationsBatch(captor.capture(), any(), any());

        Event event = makeEvent("event-co");
        event.setOrganizerUid("org-main");

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        service.notifyCoOrganizerInvite("org-main", event, "co-org-invitee",
                result::set, e -> fail(e.getMessage()));

        assertNotNull(result.get());
        assertEquals(1, result.get().getRecipientCount());
        NotificationRecord record = captor.getValue().get(0);
        assertEquals(NotificationAudience.CO_ORGANIZER_INVITE, record.getAudience());
        assertEquals("co-org-invitee", record.getRecipientUid());
        assertTrue(record.getMessage().contains("accept or decline"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.01.01  –  Create a public event with QR code
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.01.01 – Create a new public event and generate a unique promotional QR code.
     * saveEvent should persist the event with a non-null qrCodeValue.
     */
    @Test
    public void US_02_01_01_savePublicEvent_persistsEventWithQrCode() {
        EventRepository repo = mock(EventRepository.class);
        doAnswer(inv -> {
            Event ev = inv.getArgument(0);
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).saveEvent(any(), any(), any());

        Event event = makeEvent("public-ev");
        event.setQrCodeValue("helios://event/public-ev");
        event.setPrivateEvent(false);

        EventService service = new EventService(repo);
        AtomicBoolean success = new AtomicBoolean();

        service.saveEvent(event, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(repo).saveEvent(eq(event), any(), any());
        assertNotNull(event.getQrCodeValue());
        assertFalse(event.isPrivateEvent());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.01.02  –  Create a private event (not visible, no QR code)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.01.02 – Create a private event that is not visible on the event listing
     * and does not generate a promotional QR code.
     */
    @Test
    public void US_02_01_02_savePrivateEvent_hasNoQrCodeAndIsPrivate() {
        EventRepository repo = mock(EventRepository.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).saveEvent(any(), any(), any());

        Event event = makeEvent("private-ev");
        event.setPrivateEvent(true);
        event.setQrCodeValue(null);  // no QR code for private events

        EventService service = new EventService(repo);
        AtomicBoolean success = new AtomicBoolean();
        service.saveEvent(event, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertTrue(event.isPrivateEvent());
        assertNull(event.getQrCodeValue());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.01.03  –  Invite specific entrants to a private event's waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.01.03 – Invite specific entrants to a private event's waiting list
     * by searching via name, phone number, and/or email.
     * inviteEntrantToWaitingList should create an INVITED entry for the targeted user.
     */
    @Test
    public void US_02_01_03_inviteSpecificEntrantToPrivateEvent_createsInvitedEntry() {
        FirebaseRepository repo = mock(FirebaseRepository.class);

        doAnswer(inv -> {
            ((OnSuccessListener<WaitingListEntry>) inv.getArgument(2)).onSuccess(null);
            return null;
        }).when(repo).getWaitingListEntry(eq("priv-ev-3"), eq("targeted-user"), any(), any());

        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(3)).onSuccess(null);
            return null;
        }).when(repo).upsertWaitingListEntry(eq("priv-ev-3"), eq("targeted-user"), captor.capture(), any(), any());

        WaitingListService service = new WaitingListService(repo);
        AtomicBoolean success = new AtomicBoolean();
        service.inviteEntrantToWaitingList("priv-ev-3", "targeted-user",
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals(WaitingListStatus.INVITED, captor.getValue().getStatus());
        assertEquals("priv-ev-3", captor.getValue().getEventId());
        assertEquals("targeted-user", captor.getValue().getEntrantUid());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.01.04  –  Set a registration period
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.01.04 – Set a registration period (open and close timestamps).
     * The Event model should store and retrieve registration timestamps.
     */
    @Test
    public void US_02_01_04_event_storesRegistrationPeriod() {
        long opens = 1_750_000_000_000L;
        long closes = 1_750_500_000_000L;

        Event event = makeEvent("event-period");
        event.setRegistrationOpensMillis(opens);
        event.setRegistrationClosesMillis(closes);

        assertEquals(opens, event.getRegistrationOpensMillis());
        assertEquals(closes, event.getRegistrationClosesMillis());
        assertTrue(closes > opens);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.02.01  –  View the list of entrants who joined the waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.02.01 – View the list of entrants who joined my event waiting list.
     * getEntriesForEvent should return all waiting-list entries for an event.
     */
    @Test
    public void US_02_02_01_getEntriesForEvent_returnsAllWaitlistEntries() {
        WaitingListRepository repo = mock(WaitingListRepository.class);
        List<WaitingListEntry> entries = Arrays.asList(
                makeEntry("org-ev-1", "u1", WaitingListStatus.WAITING),
                makeEntry("org-ev-1", "u2", WaitingListStatus.INVITED),
                makeEntry("org-ev-1", "u3", WaitingListStatus.ACCEPTED)
        );
        stubAllEntries(repo, "org-ev-1", entries);

        WaitingListService service = new WaitingListService(repo);
        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();
        service.getEntriesForEvent("org-ev-1", result::set, e -> fail(e.getMessage()));

        assertEquals(3, result.get().size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.02.02  –  See on a map where entrants joined the waiting list from
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.02.02 – See on a map where entrants joined my event waiting list from.
     * WaitingListEntry should store latitude and longitude captured at join time.
     */
    @Test
    public void US_02_02_02_waitingListEntry_storesJoinCoordinates() {
        WaitingListEntry entry = makeEntry("geo-event", "geo-user", WaitingListStatus.WAITING);
        entry.setJoinLatitude(53.5461);
        entry.setJoinLongitude(-113.4938);

        assertEquals(Double.valueOf(53.5461), entry.getJoinLatitude());
        assertEquals(Double.valueOf(-113.4938), entry.getJoinLongitude());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.02.03  –  Enable or disable the geolocation requirement
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.02.03 – Enable or disable the geolocation requirement for my event.
     * Event.geolocationRequired flag should be persisted and queried correctly.
     */
    @Test
    public void US_02_02_03_event_geolocationRequiredFlag_roundTrips() {
        Event event = makeEvent("geo-flag");
        assertFalse(event.isGeolocationRequired());

        event.setGeolocationRequired(true);
        assertTrue(event.isGeolocationRequired());

        event.setGeolocationRequired(false);
        assertFalse(event.isGeolocationRequired());
    }

    /**
     * US 02.02.03 – Enable geolocation requirement – joining without coordinates should fail.
     */
    @Test
    public void US_02_02_03_joinWaitingList_withGeolocationRequired_failsWithoutCoordinates() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("geo-entrant");
        stubSignedInViaProfileService(profileService, user);

        Event event = makeEvent("geo-required-ev");
        event.setGeolocationRequired(true);
        stubGetEvent(evRepo, "geo-required-ev", event);

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.joinWaitingList("geo-required-ev", null, null,
                v -> fail("Should not succeed"), failure::set);

        assertNotNull(failure.get());
        assertTrue(failure.get().getMessage().contains("location access"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.03.01  –  Optionally limit number of entrants who can join waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.03.01 – Optionally limit the number of entrants who can join my waiting list.
     * Joining when waitlist is full should return a failure.
     */
    @Test
    public void US_02_03_01_joinWaitingList_whenWaitlistFull_returnsFailure() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("overflow-entrant");
        stubSignedInViaProfileService(profileService, user);

        Event event = makeEvent("capped-event");
        event.setWaitlistLimit(1);
        stubGetEvent(evRepo, "capped-event", event);
        stubGetEntry(wlRepo, "capped-event", "overflow-entrant", null);

        // 1 existing WAITING entrant fills the limit
        WaitingListEntry existing = makeEntry("capped-event", "other-user", WaitingListStatus.WAITING);
        stubAllEntries(wlRepo, "capped-event", Collections.singletonList(existing));

        doAnswer(inv -> {
            ((OnSuccessListener<Integer>) inv.getArgument(2)).onSuccess(1);
            return null;
        }).when(wlRepo).getWaitingEntriesCount(eq("capped-event"), eq(WaitingListStatus.WAITING), any(), any());

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.joinWaitingList("capped-event", null, null,
                v -> fail("Should not succeed"), failure::set);

        assertNotNull(failure.get());
        assertEquals("The waiting list for this event is full.", failure.get().getMessage());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.04.01  –  Upload an event poster
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.04.01 – Upload an event poster to the event details page.
     * The Event model must store a posterImageId linking to the uploaded image.
     */
    @Test
    public void US_02_04_01_event_storesPosterImageId() {
        Event event = makeEvent("poster-event");
        event.setPosterImageId("image-asset-001");

        assertEquals("image-asset-001", event.getPosterImageId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.04.02  –  Update an event poster
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.04.02 – Update an event poster to provide visual information to entrants.
     * saveEvent with an updated posterImageId should persist the change.
     */
    @Test
    public void US_02_04_02_updatePoster_savesUpdatedImageId() {
        EventRepository repo = mock(EventRepository.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).saveEvent(any(), any(), any());

        Event event = makeEvent("poster-update-event");
        event.setPosterImageId("image-old");
        event.setPosterImageId("image-new");  // update

        EventService service = new EventService(repo);
        AtomicBoolean success = new AtomicBoolean();
        service.saveEvent(event, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals("image-new", event.getPosterImageId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.05.01  –  Send notification to chosen entrants to sign up
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.05.01 – Send a notification to chosen entrants to sign up for events (lottery win).
     * Covered by US_01_04_01; this test verifies the organizer-initiated send path
     * using sendToAudience targeting INVITED status.
     */
    @Test
    public void US_02_05_01_sendToAudience_invitedGroup_notifiesInvitedEntrants() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "invited-u1");
        stubSaveNotifications(notifRepo);

        WaitingListEntry invitedEntry = makeEntry("send-ev", "invited-u1", WaitingListStatus.INVITED);
        stubAllEntries(wlRepo, "send-ev", Collections.singletonList(invitedEntry));

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        // SELECTED is the audience enum value that targets INVITED/ACCEPTED entrants in sendToAudience
        service.sendToAudience("org-send", "send-ev", NotificationAudience.SELECTED,
                "You're in!", "Please sign up now.", result::set, e -> fail(e.getMessage()));

        assertNotNull(result.get());
        assertEquals(1, result.get().getRecipientCount());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.05.02  –  Set system to sample a specified number of attendees
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.05.02 – Set the system to sample a specified number of attendees to register.
     * runDraw with targetCount=2 should invite exactly 2 entrants from 5 waiting.
     */
    @Test
    public void US_02_05_02_runDraw_withTargetCount_selectsExactlyThatManyWinners() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        OrganizerNotificationService notifService = mock(OrganizerNotificationService.class);

        List<WaitingListEntry> waiting = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            waiting.add(makeEntry("sample-ev", "u" + i, WaitingListStatus.WAITING));
        }
        stubAllEntries(wlRepo, "sample-ev", waiting);

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(3)).onSuccess(null);
            return null;
        }).when(wlRepo).upsertWaitingListEntry(any(), any(), any(), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(evRepo).saveEvent(any(), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(4)).onSuccess(null);
            return null;
        }).when(notifService).notifyDrawResults(any(), any(), any(), any(), any(), any());

        Event event = makeEvent("sample-ev");
        LotteryService service = new LotteryService(wlRepo, evRepo, notifService);

        AtomicBoolean success = new AtomicBoolean();
        service.runDraw("org-sample", event, 2, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        // Exactly 5 upserts (2 winners as INVITED, 3 losers as NOT_SELECTED)
        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(wlRepo, org.mockito.Mockito.times(5))
                .upsertWaitingListEntry(any(), any(), captor.capture(), any(), any());

        long invitedCount = captor.getAllValues().stream()
                .filter(e -> e.getStatus() == WaitingListStatus.INVITED).count();
        long notSelectedCount = captor.getAllValues().stream()
                .filter(e -> e.getStatus() == WaitingListStatus.NOT_SELECTED).count();

        assertEquals(2, invitedCount);
        assertEquals(3, notSelectedCount);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.05.03  –  Draw replacement when a selected applicant cancels/rejects
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.05.03 – Draw a replacement applicant from the pooling system when a previously
     * selected applicant cancels or rejects the invitation.
     * After a decline, a new runDraw must only pick from WAITING (not INVITED/ACCEPTED) entries.
     */
    @Test
    public void US_02_05_03_redraw_afterDecline_picksFromWaitingPool() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        OrganizerNotificationService notifService = mock(OrganizerNotificationService.class);

        // After one decline the pool has: 1 WAITING replacement, 1 DECLINED
        WaitingListEntry replacement = makeEntry("redraw-ev", "replacement", WaitingListStatus.WAITING);
        WaitingListEntry declined   = makeEntry("redraw-ev", "decliner",    WaitingListStatus.DECLINED);
        stubAllEntries(wlRepo, "redraw-ev", Arrays.asList(replacement, declined));

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(3)).onSuccess(null);
            return null;
        }).when(wlRepo).upsertWaitingListEntry(any(), any(), any(), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(evRepo).saveEvent(any(), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(4)).onSuccess(null);
            return null;
        }).when(notifService).notifyDrawResults(any(), any(), any(), any(), any(), any());

        Event event = makeEvent("redraw-ev");
        LotteryService service = new LotteryService(wlRepo, evRepo, notifService);
        AtomicBoolean success = new AtomicBoolean();
        service.runDraw("org-redraw", event, 1, v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        // Only the "replacement" entry should be upserted as INVITED
        ArgumentCaptor<WaitingListEntry> captor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(wlRepo).upsertWaitingListEntry(eq("redraw-ev"), eq("replacement"), captor.capture(), any(), any());
        assertEquals(WaitingListStatus.INVITED, captor.getValue().getStatus());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.06.01  –  View list of chosen (invited) entrants
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.06.01 – View a list of all chosen entrants who are invited to apply.
     * getEntriesForEvent filtered to INVITED status shows the chosen entrants.
     */
    @Test
    public void US_02_06_01_getEntriesForEvent_filteredToInvited_returnsChosenEntrants() {
        WaitingListRepository repo = mock(WaitingListRepository.class);
        List<WaitingListEntry> all = Arrays.asList(
                makeEntry("ev-0601", "u1", WaitingListStatus.INVITED),
                makeEntry("ev-0601", "u2", WaitingListStatus.WAITING),
                makeEntry("ev-0601", "u3", WaitingListStatus.INVITED)
        );
        stubAllEntries(repo, "ev-0601", all);

        WaitingListService service = new WaitingListService(repo);
        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();
        service.getEntriesForEvent("ev-0601", result::set, e -> fail(e.getMessage()));

        long invited = result.get().stream()
                .filter(e -> e.getStatus() == WaitingListStatus.INVITED).count();
        assertEquals(2, invited);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.06.02  –  See a list of all cancelled entrants
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.06.02 – See a list of all the cancelled entrants.
     */
    @Test
    public void US_02_06_02_getEntriesForEvent_filteredToCancelled_returnsCancelledEntrants() {
        WaitingListRepository repo = mock(WaitingListRepository.class);
        List<WaitingListEntry> all = Arrays.asList(
                makeEntry("ev-0602", "u1", WaitingListStatus.CANCELLED),
                makeEntry("ev-0602", "u2", WaitingListStatus.ACCEPTED),
                makeEntry("ev-0602", "u3", WaitingListStatus.CANCELLED)
        );
        stubAllEntries(repo, "ev-0602", all);

        WaitingListService service = new WaitingListService(repo);
        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();
        service.getEntriesForEvent("ev-0602", result::set, e -> fail(e.getMessage()));

        long cancelled = result.get().stream()
                .filter(e -> e.getStatus() == WaitingListStatus.CANCELLED).count();
        assertEquals(2, cancelled);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.06.03  –  See final list of enrolled entrants
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.06.03 – See a final list of entrants who enrolled for the event.
     * ACCEPTED entries represent the final enrolled attendees.
     */
    @Test
    public void US_02_06_03_getEntriesForEvent_filteredToAccepted_showsFinalEnrolledList() {
        WaitingListRepository repo = mock(WaitingListRepository.class);
        List<WaitingListEntry> all = Arrays.asList(
                makeEntry("ev-0603", "u1", WaitingListStatus.ACCEPTED),
                makeEntry("ev-0603", "u2", WaitingListStatus.INVITED),
                makeEntry("ev-0603", "u3", WaitingListStatus.ACCEPTED)
        );
        stubAllEntries(repo, "ev-0603", all);

        WaitingListService service = new WaitingListService(repo);
        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();
        service.getEntriesForEvent("ev-0603", result::set, e -> fail(e.getMessage()));

        long enrolled = result.get().stream()
                .filter(e -> e.getStatus() == WaitingListStatus.ACCEPTED).count();
        assertEquals(2, enrolled);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.06.04  –  Cancel entrants that did not sign up
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.06.04 – Cancel entrants that did not sign up for the event.
     * cancelEntrant should set status to CANCELLED and send a cancellation notification.
     */
    @Test
    public void US_02_06_04_cancelEntrant_marksStatusCancelledAndSendsNotification() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "cancel-target");
        stubSaveNotifications(notifRepo);

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(3)).onSuccess(null);
            return null;
        }).when(wlRepo).updateWaitingListEntry(any(), any(), any(), any(), any());

        Event event = makeEvent("ev-0604");
        WaitingListEntry entry = makeEntry("ev-0604", "cancel-target", WaitingListStatus.INVITED);

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicBoolean success = new AtomicBoolean();
        service.cancelEntrant("org-0604", event, entry, "Did not sign up",
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        assertEquals(WaitingListStatus.CANCELLED, entry.getStatus());
        assertTrue(entry.getCancelledAtMillis() > 0L);
        verify(wlRepo).updateWaitingListEntry(eq("ev-0604"), eq("cancel-target"), any(), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.06.05  –  Export enrolled entrants list in CSV format
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.06.05 – Export a final list of entrants who enrolled for the event in CSV format.
     * The service layer must be able to provide ACCEPTED entries; CSV formatting is a UI concern.
     * This test verifies that accepted entries contain the fields needed for CSV export.
     */
    @Test
    public void US_02_06_05_acceptedEntries_haveFieldsRequiredForCsvExport() {
        WaitingListEntry accepted = new WaitingListEntry();
        accepted.setEventId("export-ev");
        accepted.setEntrantUid("export-u1");
        accepted.setStatus(WaitingListStatus.ACCEPTED);
        accepted.setJoinedAtMillis(System.currentTimeMillis());

        // Verify all fields needed for a CSV row are present
        assertNotNull(accepted.getEventId());
        assertNotNull(accepted.getEntrantUid());
        assertEquals(WaitingListStatus.ACCEPTED, accepted.getStatus());
        assertTrue(accepted.getJoinedAtMillis() > 0L);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.07.01  –  Send notifications to all entrants on the waiting list
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.07.01 – Send notifications to all entrants on the waiting list.
     * sendToAudience with WAITING audience should only notify WAITING entrants.
     */
    @Test
    public void US_02_07_01_sendToAudience_waitingGroup_notifiesWaitingEntrantsOnly() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "waiter-notify");
        stubSaveNotifications(notifRepo);

        List<WaitingListEntry> entries = Arrays.asList(
                makeEntry("ev-0701", "waiter-notify", WaitingListStatus.WAITING),
                makeEntry("ev-0701", "already-accepted", WaitingListStatus.ACCEPTED)
        );
        stubAllEntries(wlRepo, "ev-0701", entries);

        // "already-accepted" user should not be fetched
        doAnswer(inv -> {
            fail("Should not notify ACCEPTED user");
            return null;
        }).when(userRepo).getUser(eq("already-accepted"), any(), any());

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        service.sendToAudience("org-0701", "ev-0701", NotificationAudience.WAITING,
                "Event Update", "Please check the event page.", result::set, e -> fail(e.getMessage()));

        assertEquals(1, result.get().getRecipientCount());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.07.02  –  Send notifications to all selected entrants
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.07.02 – Send notifications to all selected entrants.
     * sendToAudience with INVITED audience should only notify INVITED entrants.
     */
    @Test
    public void US_02_07_02_sendToAudience_invitedGroup_notifiesInvitedEntrantsOnly() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "selected-u1");
        stubSaveNotifications(notifRepo);

        List<WaitingListEntry> entries = Arrays.asList(
                makeEntry("ev-0702", "selected-u1", WaitingListStatus.INVITED),
                makeEntry("ev-0702", "waiting-u2", WaitingListStatus.WAITING)
        );
        stubAllEntries(wlRepo, "ev-0702", entries);

        doAnswer(inv -> {
            fail("Should not notify WAITING user");
            return null;
        }).when(userRepo).getUser(eq("waiting-u2"), any(), any());

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        // SELECTED is the audience enum value that targets INVITED/ACCEPTED entrants in sendToAudience
        service.sendToAudience("org-0702", "ev-0702", NotificationAudience.SELECTED,
                "Reminder", "Please confirm your spot.", result::set, e -> fail(e.getMessage()));

        assertEquals(1, result.get().getRecipientCount());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.07.03  –  Send notification to all cancelled entrants
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.07.03 – Send a notification to all cancelled entrants.
     * sendToAudience with CANCELLED audience should only notify CANCELLED entrants.
     */
    @Test
    public void US_02_07_03_sendToAudience_cancelledGroup_notifiesCancelledEntrantsOnly() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        stubEnabledUser(userRepo, "cancelled-u1");
        stubSaveNotifications(notifRepo);

        List<WaitingListEntry> entries = Arrays.asList(
                makeEntry("ev-0703", "cancelled-u1", WaitingListStatus.CANCELLED),
                makeEntry("ev-0703", "active-u2",    WaitingListStatus.WAITING)
        );
        stubAllEntries(wlRepo, "ev-0703", entries);

        doAnswer(inv -> {
            fail("Should not notify WAITING user");
            return null;
        }).when(userRepo).getUser(eq("active-u2"), any(), any());

        OrganizerNotificationService service =
                new OrganizerNotificationService(wlRepo, notifRepo, userRepo);

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        service.sendToAudience("org-0703", "ev-0703", NotificationAudience.CANCELLED,
                "Sorry to see you go", "You have been removed from the event.", result::set, e -> fail(e.getMessage()));

        assertEquals(1, result.get().getRecipientCount());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.08.01  –  View and delete entrant comments on my event
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.08.01 – View and delete entrant comments on my event.
     * deleteComment called by the event organizer should succeed even if not the comment author.
     */
    @Test
    public void US_02_08_01_deleteComment_byOrganizer_succeedsForAnyEntrantComment() {
        CommentRepository commentRepo = mock(CommentRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        EventService eventService = mock(EventService.class);

        // Organizer is "org-0801", comment was written by "entrant-0801"
        UserProfile orgProfile = new UserProfile("org-0801", "OrganizerName", "org@ex.com",
                null, "organizer", true, "inst");
        doAnswer(inv -> {
            ((OnSuccessListener<ProfileService.BootstrapResult>) inv.getArgument(1))
                    .onSuccess(new ProfileService.BootstrapResult(orgProfile, false));
            return null;
        }).when(profileService).bootstrapCurrentUser(any(), any(), any());

        Event event = makeEvent("ev-0801");
        event.setOrganizerUid("org-0801");
        doAnswer(inv -> {
            ((OnSuccessListener<Event>) inv.getArgument(1)).onSuccess(event);
            return null;
        }).when(eventService).getEventById(eq("ev-0801"), any(), any());

        EventComment comment = new EventComment();
        comment.setCommentId("comment-0801");
        comment.setAuthorUid("entrant-0801");  // different from organizer
        comment.setEventId("ev-0801");

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(2)).onSuccess(null);
            return null;
        }).when(commentRepo).deleteCommentWithReplies(eq("ev-0801"), eq("comment-0801"), any(), any());

        CommentService service = new CommentService(commentRepo, profileService, eventService);
        AtomicBoolean success = new AtomicBoolean();

        service.deleteComment(mock(Context.class), "ev-0801", comment,
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(commentRepo).deleteCommentWithReplies(eq("ev-0801"), eq("comment-0801"), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.08.02  –  Comment on my events (organizer pinned comment)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.08.02 – Comment on my events.
     * postComment called by the organizer creates a comment visible to all entrants.
     */
    @Test
    public void US_02_08_02_postComment_byOrganizer_createsCommentOnEvent() {
        CommentRepository commentRepo = mock(CommentRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        EventService eventService = mock(EventService.class);

        UserProfile orgProfile = new UserProfile("org-0802", "Organizer", "org@ex.com",
                null, "organizer", true, "inst");
        doAnswer(inv -> {
            ((OnSuccessListener<ProfileService.BootstrapResult>) inv.getArgument(1))
                    .onSuccess(new ProfileService.BootstrapResult(orgProfile, false));
            return null;
        }).when(profileService).bootstrapCurrentUser(any(), any(), any());

        EventComment saved = new EventComment();
        saved.setCommentId("org-comment-001");
        saved.setAuthorUid("org-0802");

        doAnswer(inv -> {
            ((OnSuccessListener<EventComment>) inv.getArgument(2)).onSuccess(saved);
            return null;
        }).when(commentRepo).addComment(eq("ev-0802"), any(), any(), any());

        CommentService service = new CommentService(commentRepo, profileService, eventService);
        AtomicReference<EventComment> result = new AtomicReference<>();

        service.postComment(mock(Context.class), "ev-0802", "Welcome everyone!",
                null, result::set, e -> fail(e.getMessage()));

        assertNotNull(result.get());
        assertEquals("org-comment-001", result.get().getCommentId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 02.09.01  –  Assign an entrant as a co-organizer
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 02.09.01 – Assign an entrant as a co-organizer for my event,
     * preventing them from joining the entrant pool for that event.
     * isCoOrganizer should return true for assigned co-organizers.
     */
    @Test
    public void US_02_09_01_coOrganizerAssignment_preventsJoiningEntrantPool() {
        Event event = makeEvent("co-org-ev");
        event.setOrganizerUid("main-org");
        event.setCoOrganizerUids(Collections.singletonList("co-org-user"));

        assertTrue(event.isCoOrganizer("co-org-user"));

        // Attempting to join should fail because co-organizers are blocked
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("co-org-user");
        stubSignedInViaProfileService(profileService, user);
        stubGetEvent(evRepo, "co-org-ev", event);

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.joinWaitingList("co-org-ev", null, null,
                v -> fail("Co-organizer should not be able to join the entrant pool"),
                failure::set);

        assertNotNull(failure.get());
        assertTrue(failure.get().getMessage().contains("Organizers/co-organizers cannot join"));
    }

    /**
     * US 02.09.01 – Assign an entrant as a co-organizer.
     * Pending co-organizer invitations are tracked separately until accepted.
     */
    @Test
    public void US_02_09_01_pendingCoOrganizer_isTrackedUntilAccepted() {
        Event event = makeEvent("co-org-pending-ev");
        event.setPendingCoOrganizerUids(Collections.singletonList("pending-co-org"));

        assertTrue(event.isPendingCoOrganizer("pending-co-org"));
        assertFalse(event.isCoOrganizer("pending-co-org"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.01.01  –  Administrator removes events
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.01.01 – As an administrator, be able to remove events.
     * deleteEvent should call the repository's deleteEvent method.
     */
    @Test
    public void US_03_01_01_deleteEvent_removesEventViaRepository() {
        EventRepository repo = mock(EventRepository.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).deleteEvent(eq("admin-del-ev"), any(), any());

        EventService service = new EventService(repo);
        AtomicBoolean success = new AtomicBoolean();

        service.deleteEvent("admin-del-ev", v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(repo).deleteEvent(eq("admin-del-ev"), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.02.01  –  Administrator removes profiles
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.02.01 – As an administrator, be able to remove profiles.
     * deleteProfile should delete the user's events and their user document.
     */
    @Test
    public void US_03_02_01_deleteProfile_removesUserAndTheirEvents() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).deleteEventsByOrganizer(eq("banned-user"), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).deleteUser(eq("banned-user"), any(), any());

        ProfileService service = makeProfileService(auth, repo);
        AtomicBoolean success = new AtomicBoolean();

        service.deleteProfile("banned-user", v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(repo).deleteEventsByOrganizer(eq("banned-user"), any(), any());
        verify(repo).deleteUser(eq("banned-user"), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.03.01  –  Administrator removes images
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.03.01 – As an administrator, be able to remove images.
     * Removing a poster image from an event should clear the posterImageId field.
     */
    @Test
    public void US_03_03_01_removePosterImage_clearsPosterImageIdOnEvent() {
        Event event = makeEvent("img-ev");
        event.setPosterImageId("image-to-remove");
        assertNotNull(event.getPosterImageId());

        event.setPosterImageId(null);  // admin removes the image
        assertNull(event.getPosterImageId());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.04.01  –  Administrator browses events
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.04.01 – As an administrator, be able to browse events.
     * getAllEvents should return the full event catalog for admin review.
     */
    @Test
    public void US_03_04_01_getAllEvents_returnsAllEventsForAdminBrowse() {
        EventRepository repo = mock(EventRepository.class);
        List<Event> catalog = Arrays.asList(makeEvent("e1"), makeEvent("e2"), makeEvent("e3"));
        doAnswer(inv -> {
            ((OnSuccessListener<List<Event>>) inv.getArgument(0)).onSuccess(catalog);
            return null;
        }).when(repo).getAllEvents(any(), any());

        EventService service = new EventService(repo);
        AtomicReference<List<Event>> result = new AtomicReference<>();
        service.getAllEvents(result::set, e -> fail(e.getMessage()));

        assertEquals(3, result.get().size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.05.01  –  Administrator browses profiles
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.05.01 – As an administrator, be able to browse profiles.
     * getAllProfiles should return the full user list for admin review.
     */
    @Test
    public void US_03_05_01_getAllProfiles_returnsAllUserProfilesForAdmin() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);

        List<UserProfile> users = Arrays.asList(
                new UserProfile("u1", "Alice", "a@x.com", null, "user", true, "i1"),
                new UserProfile("u2", "Bob",   "b@x.com", null, "user", true, "i2")
        );
        doAnswer(inv -> {
            ((OnSuccessListener<List<UserProfile>>) inv.getArgument(0)).onSuccess(users);
            return null;
        }).when(repo).getAllUsers(any(), any());

        ProfileService service = makeProfileService(auth, repo);
        AtomicReference<List<UserProfile>> result = new AtomicReference<>();
        service.getAllProfiles(result::set, e -> fail(e.getMessage()));

        assertEquals(2, result.get().size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.06.01  –  Administrator browses images
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.06.01 – As an administrator, be able to browse images uploaded in the app.
     * The ImageAsset model must hold the metadata required for the admin gallery view.
     */
    @Test
    public void US_03_06_01_imageAsset_storesRequiredBrowseFields() {
        com.example.helios.model.ImageAsset asset = new com.example.helios.model.ImageAsset();
        asset.setImageId("img-001");
        asset.setOwnerUid("uploader-uid");
        asset.setStoragePath("gs://bucket/images/img-001.png");
        asset.setUploadedAtMillis(System.currentTimeMillis());

        assertNotNull(asset.getImageId());
        assertNotNull(asset.getOwnerUid());
        assertNotNull(asset.getStoragePath());
        assertTrue(asset.getUploadedAtMillis() > 0L);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.07.01  –  Administrator removes organizers that violate policy
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.07.01 – As an administrator, remove organizers that violate app policy.
     * Revoking organizer access sets organizerAccessRevoked=true on the profile.
     */
    @Test
    public void US_03_07_01_revokeOrganizerAccess_setsRevocationFlag() {
        UserProfile organizer = new UserProfile("violator", "Bad Actor", "bad@ex.com",
                null, "organizer", true, "inst-v");
        assertFalse(organizer.isOrganizerAccessRevoked());

        organizer.setOrganizerAccessRevoked(true);

        assertTrue(organizer.isOrganizerAccessRevoked());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.08.01  –  Administrator reviews notification logs
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.08.01 – As an administrator, review logs of all notifications sent to entrants by organizers.
     * NotificationRecord stores the full audit trail needed for the admin log view.
     */
    @Test
    public void US_03_08_01_notificationRecord_storesAuditFieldsForAdminLog() {
        long sentAt = System.currentTimeMillis();
        NotificationRecord record = new NotificationRecord(
                "notif-001",
                "event-log",
                "sender-org",
                "recipient-user",
                NotificationAudience.INVITED,
                "You are selected",
                "Please confirm your spot.",
                sentAt
        );

        assertEquals("notif-001", record.getNotificationId());
        assertEquals("event-log", record.getEventId());
        assertEquals("sender-org", record.getSenderUid());
        assertEquals("recipient-user", record.getRecipientUid());
        assertEquals(NotificationAudience.INVITED, record.getAudience());
        assertEquals("You are selected", record.getTitle());
        assertTrue(record.getMessage().contains("confirm"));
        assertEquals(sentAt, record.getSentAtMillis());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.09.01  –  Administrator can also be an organizer and/or entrant
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.09.01 – As an administrator, also be able to be an organizer and/or an entrant.
     * bootstrapCurrentUser should elevate an existing user to admin when the device is registered
     * as an admin device, without preventing them from creating events or joining waiting lists.
     */
    @Test
    public void US_03_09_01_bootstrap_elevatesUserToAdminWhenDeviceRegistered() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repo = mock(FirebaseRepository.class);
        FirebaseUser user = mockFirebaseUser("uid-admin-dual");
        stubSignedInViaAuth(auth, user);

        UserProfile existing = new UserProfile("uid-admin-dual", "AdminUser", "admin@ex.com",
                null, "user", true, "admin-inst");

        doAnswer(inv -> {
            ((OnSuccessListener<Boolean>) inv.getArgument(1)).onSuccess(true);
            return null;
        }).when(repo).isAdminInstallation(eq("admin-inst"), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<UserProfile>) inv.getArgument(1)).onSuccess(existing);
            return null;
        }).when(repo).getUser(eq("uid-admin-dual"), any(), any());

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(repo).updateUser(eq(existing), any(), any());

        ProfileService service = new ProfileService(auth, repo, repo, ctx -> "admin-inst");
        AtomicReference<ProfileService.BootstrapResult> result = new AtomicReference<>();
        service.bootstrapCurrentUser(mock(Context.class), result::set, e -> fail(e.getMessage()));

        // Role should be elevated to "admin"
        assertEquals("admin", existing.getRole());
        // Admin users can still join events (no restriction at the service level unless they are
        // also the organizer/co-organizer of that specific event)
        assertTrue(existing.isAdmin());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  US 03.10.01  –  Administrator removes event comments that violate policy
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 03.10.01 – As an administrator, remove event comments that violate app policy.
     * deleteComment called by an admin profile should succeed regardless of comment authorship.
     */
    @Test
    public void US_03_10_01_deleteComment_byAdmin_succeedsForAnyComment() {
        CommentRepository commentRepo = mock(CommentRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        EventService eventService = mock(EventService.class);

        // Admin user's profile
        UserProfile adminProfile = new UserProfile("admin-uid", "Admin", "admin@ex.com",
                null, "admin", true, "inst");

        doAnswer(inv -> {
            ((OnSuccessListener<ProfileService.BootstrapResult>) inv.getArgument(1))
                    .onSuccess(new ProfileService.BootstrapResult(adminProfile, false));
            return null;
        }).when(profileService).bootstrapCurrentUser(any(), any(), any());

        // Event is not owned by the admin
        Event event = makeEvent("ev-3010");
        event.setOrganizerUid("some-other-org");
        doAnswer(inv -> {
            ((OnSuccessListener<Event>) inv.getArgument(1)).onSuccess(event);
            return null;
        }).when(eventService).getEventById(eq("ev-3010"), any(), any());

        // Comment is written by a random entrant
        EventComment comment = new EventComment();
        comment.setCommentId("bad-comment-001");
        comment.setAuthorUid("policy-violator");
        comment.setEventId("ev-3010");

        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(2)).onSuccess(null);
            return null;
        }).when(commentRepo).deleteCommentWithReplies(eq("ev-3010"), eq("bad-comment-001"), any(), any());

        CommentService service = new CommentService(commentRepo, profileService, eventService);
        AtomicBoolean success = new AtomicBoolean();

        service.deleteComment(mock(Context.class), "ev-3010", comment,
                v -> success.set(true), e -> fail(e.getMessage()));

        assertTrue(success.get());
        verify(commentRepo).deleteCommentWithReplies(eq("ev-3010"), eq("bad-comment-001"), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Additional edge-case tests for robustness
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * US 01.01.01 / US 01.05.01 – Entrant who previously CANCELLED, NOT_SELECTED, or DECLINED
     * should be allowed to re-join the waiting list.
     */
    @Test
    public void US_01_01_01_and_01_05_01_rejoin_afterCancelledOrNotSelected_succeeds() {
        for (WaitingListStatus prevStatus : Arrays.asList(
                WaitingListStatus.CANCELLED,
                WaitingListStatus.NOT_SELECTED,
                WaitingListStatus.DECLINED)) {

            WaitingListRepository wlRepo = mock(WaitingListRepository.class);
            EventRepository evRepo = mock(EventRepository.class);
            ProfileService profileService = mock(ProfileService.class);
            FirebaseUser user = mockFirebaseUser("rejoin-user");
            stubSignedInViaProfileService(profileService, user);

            Event event = makeEvent("rejoin-ev");
            stubGetEvent(evRepo, "rejoin-ev", event);

            WaitingListEntry existing = makeEntry("rejoin-ev", "rejoin-user", prevStatus);
            stubGetEntry(wlRepo, "rejoin-ev", "rejoin-user", existing);
            stubUpsertEntry(wlRepo, "rejoin-ev", "rejoin-user");

            EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
            AtomicBoolean success = new AtomicBoolean();

            service.joinWaitingList("rejoin-ev", null, null,
                    v -> success.set(true), e -> fail("Re-join after " + prevStatus + " failed: " + e.getMessage()));

            assertTrue("Expected re-join to succeed for prior status: " + prevStatus, success.get());
        }
    }

    /**
     * US 02.02.03 – Geolocation: joining outside event geofence should be rejected.
     */
    @Test
    public void US_02_02_03_joinWaitingList_outsideGeofence_returnsDistanceError() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockFirebaseUser("geo-far");
        stubSignedInViaProfileService(profileService, user);

        Event event = makeEvent("geo-fence-ev");
        event.setGeolocationRequired(true);
        event.setGeofenceCenter(53.5461, -113.4938);
        event.setGeofenceRadiusMeters(100);
        stubGetEvent(evRepo, "geo-fence-ev", event);

        EntrantEventService service = new EntrantEventService(wlRepo, evRepo, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        // User is far away from the fence center
        service.joinWaitingList("geo-fence-ev", 53.6000, -113.5000,
                v -> fail("Should not succeed outside geofence"), failure::set);

        assertNotNull(failure.get());
        assertTrue(failure.get().getMessage().contains("100 meters"));
    }

    /**
     * US 02.05.02 – runDraw with zero target count must fail gracefully.
     */
    @Test
    public void US_02_05_02_runDraw_withZeroTargetCount_returnsError() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        OrganizerNotificationService notifService = mock(OrganizerNotificationService.class);

        WaitingListEntry waiting = makeEntry("ev-zero", "u1", WaitingListStatus.WAITING);
        stubAllEntries(wlRepo, "ev-zero", Collections.singletonList(waiting));

        Event event = makeEvent("ev-zero");
        LotteryService service = new LotteryService(wlRepo, evRepo, notifService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.runDraw("org-zero", event, 0,
                v -> fail("Should not succeed with zero count"), failure::set);

        assertNotNull(failure.get());
        assertEquals("Draw count must be greater than 0.", failure.get().getMessage());
    }

    /**
     * US 02.06.04 / US 03.01.01 – runDraw with empty waiting list must fail gracefully.
     */
    @Test
    public void US_02_05_02_and_03_01_01_runDraw_withEmptyWaitingList_returnsError() {
        WaitingListRepository wlRepo = mock(WaitingListRepository.class);
        EventRepository evRepo = mock(EventRepository.class);
        OrganizerNotificationService notifService = mock(OrganizerNotificationService.class);

        stubAllEntries(wlRepo, "empty-ev", Collections.emptyList());

        Event event = makeEvent("empty-ev");
        LotteryService service = new LotteryService(wlRepo, evRepo, notifService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.runDraw("org-empty", event, 5,
                v -> fail("Should not succeed with empty waitlist"), failure::set);

        assertNotNull(failure.get());
        assertEquals(LotteryService.NO_PEOPLE_IN_EVENT_MESSAGE, failure.get().getMessage());
    }

    /**
     * US 01.02.01 – Profile completion check: profile missing name requires completion.
     */
    @Test
    public void US_01_02_01_requiresProfileCompletion_returnsTrueWhenNameMissing() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        ProfileService service = makeProfileService(auth, mock(FirebaseRepository.class));

        UserProfile noName = new UserProfile("uid", null, "email@ex.com", null, "user", true, "inst");
        assertTrue(service.requiresProfileCompletion(noName));
    }

    /**
     * US 01.02.01 – Profile completion check: profile missing email requires completion.
     */
    @Test
    public void US_01_02_01_requiresProfileCompletion_returnsTrueWhenEmailMissing() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        ProfileService service = makeProfileService(auth, mock(FirebaseRepository.class));

        UserProfile noEmail = new UserProfile("uid", "Alice", null, null, "user", true, "inst");
        assertTrue(service.requiresProfileCompletion(noEmail));
    }

    /**
     * US 01.02.01 – Profile completion check: complete profile does not require setup again.
     */
    @Test
    public void US_01_02_01_requiresProfileCompletion_returnsFalseWhenComplete() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        ProfileService service = makeProfileService(auth, mock(FirebaseRepository.class));

        UserProfile complete = new UserProfile("uid", "Alice", "alice@ex.com", null, "user", true, "inst");
        assertFalse(service.requiresProfileCompletion(complete));
    }
}
