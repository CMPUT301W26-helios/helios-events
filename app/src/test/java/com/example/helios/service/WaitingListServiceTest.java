package com.example.helios.service;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

public class WaitingListServiceTest {

    @Test
    public void getEntriesForEvent_passesEventIdAndReturnsEntries() {
        FirebaseRepository repository = mock(FirebaseRepository.class);

        List<WaitingListEntry> expectedEntries = new ArrayList<>();
        expectedEntries.add(new WaitingListEntry());
        expectedEntries.add(new WaitingListEntry());

        doAnswer(invocation -> {
            OnSuccessListener<List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(expectedEntries);
            return null;
        }).when(repository).getAllWaitingListEntries(eq("event-a"), any(), any());

        WaitingListService service = new WaitingListService(repository);

        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();
        service.getEntriesForEvent(
                "event-a",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertNotNull(result.get());
        assertEquals(2, result.get().size());
        assertSame(expectedEntries, result.get());
        verify(repository).getAllWaitingListEntries(eq("event-a"), any(), any());
    }

    @Test
    public void updateEntry_withBlankEntrantUid_returnsFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        WaitingListService service = new WaitingListService(repository);

        WaitingListEntry entry = new WaitingListEntry();
        entry.setEntrantUid("   ");

        AtomicReference<Exception> failure = new AtomicReference<>();

        service.updateEntry(
                "event-a",
                entry,
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertNotNull(failure.get());
        assertTrue(failure.get() instanceof IllegalArgumentException);
        assertEquals("entrantUid must not be empty.", failure.get().getMessage());
        verify(repository, never()).updateWaitingListEntry(any(), any(), any(), any(), any());
    }

    @Test
    public void updateEntry_withValidEntrantUid_delegatesToRepository() {
        FirebaseRepository repository = mock(FirebaseRepository.class);

        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            String entrantUid = invocation.getArgument(1);
            WaitingListEntry entryArg = invocation.getArgument(2);
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);

            assertEquals("event-1", eventId);
            assertEquals("user-1", entrantUid);
            assertEquals("user-1", entryArg.getEntrantUid());

            onSuccess.onSuccess(null);
            return null;
        }).when(repository).updateWaitingListEntry(eq("event-1"), eq("user-1"), any(), any(), any());

        WaitingListService service = new WaitingListService(repository);

        WaitingListEntry entry = new WaitingListEntry();
        entry.setEntrantUid("user-1");

        AtomicBoolean successCalled = new AtomicBoolean(false);
        service.updateEntry(
                "event-1",
                entry,
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).updateWaitingListEntry(eq("event-1"), eq("user-1"), eq(entry), any(), any());
    }

    @Test
    public void removeEntry_passesIdsToRepository() {
        FirebaseRepository repository = mock(FirebaseRepository.class);

        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            String entrantUid = invocation.getArgument(1);
            OnSuccessListener<Void> onSuccess = invocation.getArgument(2);

            assertEquals("event-z", eventId);
            assertEquals("entrant-z", entrantUid);

            onSuccess.onSuccess(null);
            return null;
        }).when(repository).deleteWaitingListEntry(eq("event-z"), eq("entrant-z"), any(), any());

        WaitingListService service = new WaitingListService(repository);

        AtomicBoolean successCalled = new AtomicBoolean(false);
        service.removeEntry(
                "event-z",
                "entrant-z",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).deleteWaitingListEntry(eq("event-z"), eq("entrant-z"), any(), any());
    }
}