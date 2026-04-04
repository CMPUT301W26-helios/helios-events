package com.example.helios.service;

import android.content.Context;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class EntrantEventServiceTest {

    private WaitingListEntry entryWithStatus(WaitingListStatus status) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setStatus(status);
        return entry;
    }

    private FirebaseUser mockUser(String uid) {
        FirebaseUser user = mock(FirebaseUser.class);
        doReturn(uid).when(user).getUid();
        return user;
    }

    private void stubSignedInUser(ProfileService profileService, FirebaseUser user) {
        doAnswer(invocation -> {
            OnSuccessListener<FirebaseUser> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(user);
            return null;
        }).when(profileService).ensureSignedIn(any(), any());
    }

    @Test
    public void getFilledSlotsCount_countsOnlySelectedStatuses() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);

        List<WaitingListEntry> entries = Arrays.asList(
                entryWithStatus(WaitingListStatus.WAITING),
                entryWithStatus(WaitingListStatus.INVITED),
                entryWithStatus(WaitingListStatus.ACCEPTED),
                entryWithStatus(WaitingListStatus.NOT_SELECTED),
                entryWithStatus(WaitingListStatus.CANCELLED),
                entryWithStatus(WaitingListStatus.DECLINED),
                entryWithStatus(null),
                null
        );

        doAnswer(invocation -> {
            OnSuccessListener<List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(entries);
            return null;
        }).when(repository).getAllWaitingListEntries(eq("event-17"), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Integer> count = new AtomicReference<>();

        service.getFilledSlotsCount("event-17", count::set, e -> fail("Unexpected failure: " + e.getMessage()));

        assertEquals(Integer.valueOf(3), count.get());
        verify(repository).getAllWaitingListEntries(eq("event-17"), any(), any());
        verifyNoMoreInteractions(profileService);
    }

    @Test
    public void getFilledSlotsCount_forwardsRepositoryFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        RuntimeException expected = new RuntimeException("boom");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(2);
            onFailure.onFailure(expected);
            return null;
        }).when(repository).getAllWaitingListEntries(eq("event-22"), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getFilledSlotsCount("event-22", count -> fail("Success should not be called"), failure::set);

        assertSame(expected, failure.get());
        verify(repository).getAllWaitingListEntries(eq("event-22"), any(), any());
        verifyNoMoreInteractions(profileService);
    }

    @Test
    public void getFilledSlotsCount_withEmptyEntries_returnsZero() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);

        doAnswer(invocation -> {
            OnSuccessListener<List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(new ArrayList<>());
            return null;
        }).when(repository).getAllWaitingListEntries(eq("event-empty"), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Integer> count = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getFilledSlotsCount("event-empty", count::set, failure::set);

        assertEquals(Integer.valueOf(0), count.get());
        assertNull(failure.get());
        verify(repository).getAllWaitingListEntries(eq("event-empty"), any(), any());
        verifyNoMoreInteractions(profileService);
    }

    @Test
    public void getCurrentUserWaitingListEntry_usesSignedInUidAndReturnsRepositoryEntry() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockUser("user-7");
        stubSignedInUser(profileService, user);

        WaitingListEntry expected = new WaitingListEntry();
        expected.setEntrantUid("user-7");
        expected.setEventId("event-7");

        doAnswer(invocation -> {
            OnSuccessListener<WaitingListEntry> onSuccess = invocation.getArgument(2);
            onSuccess.onSuccess(expected);
            return null;
        }).when(repository).getWaitingListEntry(eq("event-7"), eq("user-7"), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<WaitingListEntry> result = new AtomicReference<>();

        service.getCurrentUserWaitingListEntry(
                mock(Context.class),
                "event-7",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(expected, result.get());
        verify(profileService).ensureSignedIn(any(), any());
        verify(repository).getWaitingListEntry(eq("event-7"), eq("user-7"), any(), any());
    }

    @Test
    public void getCurrentUserWaitingListEntry_forwardsAuthFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        RuntimeException expected = new RuntimeException("auth failed");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(1);
            onFailure.onFailure(expected);
            return null;
        }).when(profileService).ensureSignedIn(any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getCurrentUserWaitingListEntry(
                mock(Context.class),
                "event-8",
                entry -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(profileService).ensureSignedIn(any(), any());
        verify(repository, never()).getWaitingListEntry(any(), any(), any(), any());
    }

    @Test
    public void getCurrentUserWaitlistEntries_usesSignedInUidAndReturnsRepositoryEntries() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockUser("user-11");
        stubSignedInUser(profileService, user);

        List<WaitingListEntry> expected = new ArrayList<>();
        WaitingListEntry invited = new WaitingListEntry();
        invited.setEntrantUid("user-11");
        invited.setEventId("event-private");
        invited.setStatus(WaitingListStatus.INVITED);
        expected.add(invited);

        doAnswer(invocation -> {
            OnSuccessListener<List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(expected);
            return null;
        }).when(repository).getWaitlistEntriesForUser(eq("user-11"), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();

        service.getCurrentUserWaitlistEntries(
                mock(Context.class),
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(expected, result.get());
        verify(profileService).ensureSignedIn(any(), any());
        verify(repository).getWaitlistEntriesForUser(eq("user-11"), any(), any());
    }

    @Test
    public void getCurrentUserWaitlistEntries_forwardsAuthFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        RuntimeException expected = new RuntimeException("entry auth failed");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(1);
            onFailure.onFailure(expected);
            return null;
        }).when(profileService).ensureSignedIn(any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getCurrentUserWaitlistEntries(
                mock(Context.class),
                entries -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(profileService).ensureSignedIn(any(), any());
        verify(repository, never()).getWaitlistEntriesForUser(any(), any(), any());
    }

    @Test
    public void joinWaitingList_createsWaitingEntryWhenNoExistingEntry() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockUser("entrant-1");
        stubSignedInUser(profileService, user);

        doAnswer(invocation -> {
            OnSuccessListener<WaitingListEntry> onSuccess = invocation.getArgument(2);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).getWaitingListEntry(eq("event-1"), eq("entrant-1"), any(), any());

        AtomicBoolean successCalled = new AtomicBoolean(false);
        ArgumentCaptor<WaitingListEntry> entryCaptor = ArgumentCaptor.forClass(WaitingListEntry.class);

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).upsertWaitingListEntry(eq("event-1"), eq("entrant-1"), entryCaptor.capture(), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);

        service.joinWaitingList(
                mock(Context.class),
                "event-1",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());

        WaitingListEntry saved = entryCaptor.getValue();
        assertEquals("event-1", saved.getEventId());
        assertEquals("entrant-1", saved.getEntrantUid());
        assertEquals(WaitingListStatus.WAITING, saved.getStatus());
        assertTrue(saved.getJoinedAtMillis() > 0L);

        verify(profileService).ensureSignedIn(any(), any());
        verify(repository).getWaitingListEntry(eq("event-1"), eq("entrant-1"), any(), any());
        verify(repository).upsertWaitingListEntry(eq("event-1"), eq("entrant-1"), any(), any(), any());
    }

    @Test
    public void joinWaitingList_withExistingActiveEntry_returnsSuccessWithoutUpsert() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockUser("entrant-2");
        stubSignedInUser(profileService, user);

        WaitingListEntry existing = entryWithStatus(WaitingListStatus.INVITED);
        existing.setEntrantUid("entrant-2");
        existing.setEventId("event-2");

        doAnswer(invocation -> {
            OnSuccessListener<WaitingListEntry> onSuccess = invocation.getArgument(2);
            onSuccess.onSuccess(existing);
            return null;
        }).when(repository).getWaitingListEntry(eq("event-2"), eq("entrant-2"), any(), any());

        AtomicBoolean successCalled = new AtomicBoolean(false);
        EntrantEventService service = new EntrantEventService(repository, profileService);

        service.joinWaitingList(
                mock(Context.class),
                "event-2",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).getWaitingListEntry(eq("event-2"), eq("entrant-2"), any(), any());
        verify(repository, never()).upsertWaitingListEntry(any(), any(), any(), any(), any());
    }

    @Test
    public void joinWaitingList_allowsRejoinAfterCancelledNotSelectedOrDeclined() {
        for (WaitingListStatus status : Arrays.asList(
                WaitingListStatus.CANCELLED,
                WaitingListStatus.NOT_SELECTED,
                WaitingListStatus.DECLINED
        )) {
            FirebaseRepository repository = mock(FirebaseRepository.class);
            ProfileService profileService = mock(ProfileService.class);
            FirebaseUser user = mockUser("entrant-rejoin");
            stubSignedInUser(profileService, user);

            WaitingListEntry existing = entryWithStatus(status);
            existing.setEntrantUid("entrant-rejoin");

            doAnswer(invocation -> {
                OnSuccessListener<WaitingListEntry> onSuccess = invocation.getArgument(2);
                onSuccess.onSuccess(existing);
                return null;
            }).when(repository).getWaitingListEntry(eq("event-rejoin"), eq("entrant-rejoin"), any(), any());

            AtomicBoolean successCalled = new AtomicBoolean(false);

            doAnswer(invocation -> {
                OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
                onSuccess.onSuccess(null);
                return null;
            }).when(repository).upsertWaitingListEntry(eq("event-rejoin"), eq("entrant-rejoin"), any(), any(), any());

            EntrantEventService service = new EntrantEventService(repository, profileService);

            service.joinWaitingList(
                    mock(Context.class),
                    "event-rejoin",
                    unused -> successCalled.set(true),
                    e -> fail("Unexpected failure: " + e.getMessage())
            );

            assertTrue("Expected success for status " + status, successCalled.get());
            verify(repository).upsertWaitingListEntry(eq("event-rejoin"), eq("entrant-rejoin"), any(), any(), any());
        }
    }

    @Test
    public void joinWaitingList_forwardsAuthFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        RuntimeException expected = new RuntimeException("auth boom");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(1);
            onFailure.onFailure(expected);
            return null;
        }).when(profileService).ensureSignedIn(any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.joinWaitingList(
                mock(Context.class),
                "event-auth",
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository, never()).getWaitingListEntry(any(), any(), any(), any());
        verify(repository, never()).upsertWaitingListEntry(any(), any(), any(), any(), any());
    }

    @Test
    public void leaveWaitingList_whenNoEntryExists_returnsSuccessWithoutUpdate() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockUser("entrant-3");
        stubSignedInUser(profileService, user);

        doAnswer(invocation -> {
            OnSuccessListener<WaitingListEntry> onSuccess = invocation.getArgument(2);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).getWaitingListEntry(eq("event-3"), eq("entrant-3"), any(), any());

        AtomicBoolean successCalled = new AtomicBoolean(false);
        EntrantEventService service = new EntrantEventService(repository, profileService);

        service.leaveWaitingList(
                mock(Context.class),
                "event-3",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).getWaitingListEntry(eq("event-3"), eq("entrant-3"), any(), any());
        verify(repository, never()).updateWaitingListEntry(any(), any(), any(), any(), any());
    }

    @Test
    public void leaveWaitingList_existingEntry_marksItCancelledAndUpdates() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        FirebaseUser user = mockUser("entrant-4");
        stubSignedInUser(profileService, user);

        WaitingListEntry existing = entryWithStatus(WaitingListStatus.WAITING);
        existing.setEntrantUid("entrant-4");
        existing.setEventId("event-4");

        doAnswer(invocation -> {
            OnSuccessListener<WaitingListEntry> onSuccess = invocation.getArgument(2);
            onSuccess.onSuccess(existing);
            return null;
        }).when(repository).getWaitingListEntry(eq("event-4"), eq("entrant-4"), any(), any());

        ArgumentCaptor<WaitingListEntry> entryCaptor = ArgumentCaptor.forClass(WaitingListEntry.class);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).updateWaitingListEntry(eq("event-4"), eq("entrant-4"), entryCaptor.capture(), any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);

        service.leaveWaitingList(
                mock(Context.class),
                "event-4",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        assertEquals(WaitingListStatus.CANCELLED, entryCaptor.getValue().getStatus());
        verify(repository).updateWaitingListEntry(eq("event-4"), eq("entrant-4"), any(), any(), any());
    }

    @Test
    public void leaveWaitingList_forwardsAuthFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService profileService = mock(ProfileService.class);
        RuntimeException expected = new RuntimeException("leave auth boom");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(1);
            onFailure.onFailure(expected);
            return null;
        }).when(profileService).ensureSignedIn(any(), any());

        EntrantEventService service = new EntrantEventService(repository, profileService);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.leaveWaitingList(
                mock(Context.class),
                "event-5",
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository, never()).getWaitingListEntry(any(), any(), any(), any());
        verify(repository, never()).updateWaitingListEntry(any(), any(), any(), any(), any());
    }
}
