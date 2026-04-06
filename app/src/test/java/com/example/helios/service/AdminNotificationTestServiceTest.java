package com.example.helios.service;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.EventRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AdminNotificationTestServiceTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void createOrRefreshSandbox_seedsSandboxEventFakeUsersAndEntries() {
        AuthDeviceService authDeviceService = mock(AuthDeviceService.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        OrganizerNotificationService organizerNotificationService = mock(OrganizerNotificationService.class);

        AdminNotificationTestService service = new AdminNotificationTestService(
                authDeviceService,
                userRepository,
                eventRepository,
                waitingListRepository,
                organizerNotificationService,
                () -> NOW
        );

        FirebaseUser firebaseUser = mock(FirebaseUser.class);
        doAnswer(invocation -> {
            OnSuccessListener<FirebaseUser> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(firebaseUser);
            return null;
        }).when(authDeviceService).ensureSignedIn(any(), any());

        doAnswer(invocation -> "admin/uid").when(firebaseUser).getUid();

        UserProfile profile = new UserProfile();
        profile.setUid("admin/uid");
        profile.setName("Admin User");
        profile.setRole("admin");
        profile.setNotificationsEnabled(true);
        profile.setInstallationId("device-installation");
        profile.setFcmToken("fcm-token");

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(profile);
            return null;
        }).when(userRepository).getUser(eq("admin/uid"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(eventRepository).saveEvent(any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(userRepository).saveUser(any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
            onSuccess.onSuccess(null);
            return null;
        }).when(waitingListRepository).upsertWaitingListEntry(any(), any(), any(), any(), any());

        AtomicReference<AdminNotificationTestService.SandboxState> result = new AtomicReference<>();
        service.createOrRefreshSandbox(
                result::set,
                error -> fail("Unexpected failure: " + error.getMessage())
        );

        assertNotNull(result.get());
        assertEquals("admin/uid", result.get().getCurrentUid());
        assertEquals("Admin User", result.get().getCurrentUserLabel());
        assertEquals(3, result.get().getFakeEntrantCount());
        assertTrue(result.get().isNotificationsEnabled());
        assertTrue(result.get().isPushReady());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveEvent(eventCaptor.capture(), any(), any());
        Event savedEvent = eventCaptor.getValue();
        assertEquals("dev_notification_sandbox_admin_uid", savedEvent.getEventId());
        assertEquals("Developer Notification Sandbox", savedEvent.getTitle());
        assertTrue(savedEvent.isPrivateEvent());

        ArgumentCaptor<UserProfile> fakeUsersCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userRepository, times(3)).saveUser(fakeUsersCaptor.capture(), any(), any());
        List<UserProfile> fakeUsers = fakeUsersCaptor.getAllValues();
        assertEquals("Sandbox Entrant 1", fakeUsers.get(0).getName());
        assertEquals("Sandbox Entrant 2", fakeUsers.get(1).getName());
        assertEquals("Sandbox Entrant 3", fakeUsers.get(2).getName());

        ArgumentCaptor<WaitingListEntry> entryCaptor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(waitingListRepository, times(4))
                .upsertWaitingListEntry(eq("dev_notification_sandbox_admin_uid"), any(), entryCaptor.capture(), any(), any());

        List<WaitingListEntry> entries = entryCaptor.getAllValues();
        assertEquals("admin/uid", entries.get(0).getEntrantUid());
        assertEquals(WaitingListStatus.WAITING, entries.get(0).getStatus());
        assertEquals(WaitingListStatus.WAITING, entries.get(1).getStatus());
        assertEquals(WaitingListStatus.ACCEPTED, entries.get(2).getStatus());
        assertEquals(WaitingListStatus.CANCELLED, entries.get(3).getStatus());
    }

    @Test
    public void simulateDrawWithAdminSelected_notifiesAdminAsWinner() {
        AuthDeviceService authDeviceService = mock(AuthDeviceService.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        OrganizerNotificationService organizerNotificationService = mock(OrganizerNotificationService.class);

        AdminNotificationTestService service = new AdminNotificationTestService(
                authDeviceService,
                userRepository,
                eventRepository,
                waitingListRepository,
                organizerNotificationService,
                () -> NOW
        );

        FirebaseUser firebaseUser = mock(FirebaseUser.class);
        doAnswer(invocation -> {
            OnSuccessListener<FirebaseUser> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(firebaseUser);
            return null;
        }).when(authDeviceService).ensureSignedIn(any(), any());

        doAnswer(invocation -> "admin-uid").when(firebaseUser).getUid();

        UserProfile profile = new UserProfile();
        profile.setUid("admin-uid");
        profile.setName("Admin");
        profile.setRole("admin");
        profile.setNotificationsEnabled(true);
        profile.setInstallationId("device-installation");

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(profile);
            return null;
        }).when(userRepository).getUser(eq("admin-uid"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(eventRepository).saveEvent(any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(userRepository).saveUser(any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
            onSuccess.onSuccess(null);
            return null;
        }).when(waitingListRepository).upsertWaitingListEntry(any(), any(), any(), any(), any());

        ArgumentCaptor<List<WaitingListEntry>> winnersCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<WaitingListEntry>> losersCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(4);
            onSuccess.onSuccess(null);
            return null;
        }).when(organizerNotificationService).notifyDrawResults(
                eq("admin-uid"),
                any(),
                winnersCaptor.capture(),
                losersCaptor.capture(),
                any(),
                any()
        );

        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        service.simulateDrawWithAdminSelected(
                unused -> completed.set(true),
                error -> fail("Unexpected failure: " + error.getMessage())
        );

        assertTrue(completed.get());

        verify(eventRepository, times(2)).saveEvent(any(), any(), any());

        List<WaitingListEntry> winners = winnersCaptor.getValue();
        List<WaitingListEntry> losers = losersCaptor.getValue();
        assertEquals(2, winners.size());
        assertEquals(2, losers.size());
        assertEquals("admin-uid", winners.get(0).getEntrantUid());
        assertEquals(WaitingListStatus.INVITED, winners.get(0).getStatus());

        boolean adminInLosers = false;
        for (WaitingListEntry loser : losers) {
            if ("admin-uid".equals(loser.getEntrantUid())) {
                adminInLosers = true;
                break;
            }
        }
        assertFalse(adminInLosers);

        ArgumentCaptor<Event> savedEventsCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, times(2)).saveEvent(savedEventsCaptor.capture(), any(), any());
        List<Event> savedEvents = savedEventsCaptor.getAllValues();
        assertEquals(1, savedEvents.get(1).getDrawCount());
    }
}
