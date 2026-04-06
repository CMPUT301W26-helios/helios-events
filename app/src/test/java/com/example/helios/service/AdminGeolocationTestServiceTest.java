package com.example.helios.service;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.EventRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AdminGeolocationTestServiceTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void createOrRefreshSandbox_seedsGeolocationRequiredSandboxAndCoordinates() {
        AuthDeviceService authDeviceService = mock(AuthDeviceService.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);

        AdminGeolocationTestService service = new AdminGeolocationTestService(
                authDeviceService,
                userRepository,
                eventRepository,
                waitingListRepository,
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

        AtomicReference<AdminGeolocationTestService.SandboxState> result = new AtomicReference<>();
        service.createOrRefreshSandbox(
                result::set,
                error -> fail("Unexpected failure: " + error.getMessage())
        );

        assertNotNull(result.get());
        assertEquals("admin/uid", result.get().getCurrentUid());
        assertEquals("Admin User", result.get().getCurrentUserLabel());
        assertEquals("Edmonton cluster", result.get().getScenarioLabel());
        assertEquals(3, result.get().getFakeEntrantCount());
        assertEquals(4, result.get().getLocationPointCount());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveEvent(eventCaptor.capture(), any(), any());
        Event savedEvent = eventCaptor.getValue();
        assertEquals("dev_geolocation_sandbox_admin_uid", savedEvent.getEventId());
        assertEquals("Developer Geolocation Sandbox", savedEvent.getTitle());
        assertTrue(savedEvent.isPrivateEvent());
        assertTrue(savedEvent.isGeolocationRequired());
        assertNotNull(savedEvent.getGeofenceCenter());
        assertEquals(53.5461, savedEvent.getGeofenceCenter().getLatitude(), 0.0001);
        assertEquals(-113.4938, savedEvent.getGeofenceCenter().getLongitude(), 0.0001);
        assertTrue(savedEvent.getGeofenceRadiusMeters() > 0);

        ArgumentCaptor<WaitingListEntry> entryCaptor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(waitingListRepository, times(4))
                .upsertWaitingListEntry(eq("dev_geolocation_sandbox_admin_uid"), any(), entryCaptor.capture(), any(), any());

        List<WaitingListEntry> entries = entryCaptor.getAllValues();
        assertEquals("admin/uid", entries.get(0).getEntrantUid());
        assertEquals(53.5461, entries.get(0).getJoinLatitude(), 0.0001);
        assertEquals(-113.4938, entries.get(0).getJoinLongitude(), 0.0001);
        assertEquals(53.5498, entries.get(1).getJoinLatitude(), 0.0001);
        assertEquals(-113.5013, entries.get(1).getJoinLongitude(), 0.0001);
    }

    @Test
    public void createRegionalSpreadSandbox_seedsRegionalCoordinates() {
        AuthDeviceService authDeviceService = mock(AuthDeviceService.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);

        AdminGeolocationTestService service = new AdminGeolocationTestService(
                authDeviceService,
                userRepository,
                eventRepository,
                waitingListRepository,
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

        AtomicReference<AdminGeolocationTestService.SandboxState> result = new AtomicReference<>();
        service.createRegionalSpreadSandbox(
                result::set,
                error -> fail("Unexpected failure: " + error.getMessage())
        );

        assertNotNull(result.get());
        assertEquals("Alberta spread", result.get().getScenarioLabel());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveEvent(eventCaptor.capture(), any(), any());
        Event savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent.getGeofenceCenter());
        assertEquals(53.5461, savedEvent.getGeofenceCenter().getLatitude(), 0.0001);
        assertEquals(-113.4938, savedEvent.getGeofenceCenter().getLongitude(), 0.0001);
        assertTrue(savedEvent.getGeofenceRadiusMeters() > 0);

        ArgumentCaptor<WaitingListEntry> entryCaptor = ArgumentCaptor.forClass(WaitingListEntry.class);
        verify(waitingListRepository, times(4))
                .upsertWaitingListEntry(eq("dev_geolocation_sandbox_admin-uid"), any(), entryCaptor.capture(), any(), any());

        List<WaitingListEntry> entries = entryCaptor.getAllValues();
        assertEquals(51.0447, entries.get(1).getJoinLatitude(), 0.0001);
        assertEquals(-114.0719, entries.get(1).getJoinLongitude(), 0.0001);
        assertEquals(52.2681, entries.get(2).getJoinLatitude(), 0.0001);
        assertEquals(-113.8112, entries.get(2).getJoinLongitude(), 0.0001);
        assertEquals(53.9333, entries.get(3).getJoinLatitude(), 0.0001);
        assertEquals(-116.5765, entries.get(3).getJoinLongitude(), 0.0001);
    }
}
