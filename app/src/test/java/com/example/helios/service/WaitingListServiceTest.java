package com.example.helios.service;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.testutil.UnsafeTestHelper;
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

public class WaitingListServiceTest {

    private static class FakeRepository extends FirebaseRepository {
        List<WaitingListEntry> entriesToReturn = new ArrayList<>();
        String listEventId;
        String updateEventId;
        String updateEntrantUid;
        WaitingListEntry updateEntry;
        String deleteEventId;
        String deleteEntrantUid;

        @Override
        public void getAllWaitingListEntries(
                @androidx.annotation.NonNull String eventId,
                @androidx.annotation.NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
                @androidx.annotation.NonNull OnFailureListener onFailure
        ) {
            listEventId = eventId;
            onSuccess.onSuccess(entriesToReturn);
        }

        @Override
        public void updateWaitingListEntry(
                @androidx.annotation.NonNull String eventId,
                @androidx.annotation.NonNull String entrantUid,
                @androidx.annotation.NonNull WaitingListEntry entry,
                @androidx.annotation.NonNull OnSuccessListener<Void> onSuccess,
                @androidx.annotation.NonNull OnFailureListener onFailure
        ) {
            updateEventId = eventId;
            updateEntrantUid = entrantUid;
            updateEntry = entry;
            onSuccess.onSuccess(null);
        }

        @Override
        public void deleteWaitingListEntry(
                @androidx.annotation.NonNull String eventId,
                @androidx.annotation.NonNull String entrantUid,
                @androidx.annotation.NonNull OnSuccessListener<Void> onSuccess,
                @androidx.annotation.NonNull OnFailureListener onFailure
        ) {
            deleteEventId = eventId;
            deleteEntrantUid = entrantUid;
            onSuccess.onSuccess(null);
        }
    }

    private WaitingListService createServiceWithFakeRepository(FakeRepository repository) {
        WaitingListService service = UnsafeTestHelper.allocateWithoutConstructor(WaitingListService.class);
        UnsafeTestHelper.setObjectField(service, "repository", repository);
        return service;
    }

    @Test
    public void getEntriesForEvent_passesEventIdAndReturnsEntries() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        repository.entriesToReturn.add(new WaitingListEntry());
        repository.entriesToReturn.add(new WaitingListEntry());
        WaitingListService service = createServiceWithFakeRepository(repository);

        AtomicReference<List<WaitingListEntry>> result = new AtomicReference<>();
        service.getEntriesForEvent("event-a", result::set, e -> fail("Unexpected failure: " + e.getMessage()));

        assertEquals("event-a", repository.listEventId);
        assertNotNull(result.get());
        assertEquals(2, result.get().size());
    }

    @Test
    public void updateEntry_withBlankEntrantUid_returnsFailure() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        WaitingListService service = createServiceWithFakeRepository(repository);
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEntrantUid("   ");

        AtomicReference<Exception> failure = new AtomicReference<>();

        service.updateEntry("event-a", entry, unused -> fail("Success should not be called"), failure::set);

        assertNotNull(failure.get());
        assertTrue(failure.get() instanceof IllegalArgumentException);
        assertEquals("entrantUid must not be empty.", failure.get().getMessage());
    }

    @Test
    public void updateEntry_withValidEntrantUid_delegatesToRepository() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        WaitingListService service = createServiceWithFakeRepository(repository);
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEntrantUid("user-1");

        AtomicBoolean successCalled = new AtomicBoolean(false);
        service.updateEntry("event-1", entry, unused -> successCalled.set(true), e -> fail("Unexpected failure: " + e.getMessage()));

        assertTrue(successCalled.get());
        assertEquals("event-1", repository.updateEventId);
        assertEquals("user-1", repository.updateEntrantUid);
        assertSame(entry, repository.updateEntry);
    }

    @Test
    public void removeEntry_passesIdsToRepository() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        WaitingListService service = createServiceWithFakeRepository(repository);

        AtomicBoolean successCalled = new AtomicBoolean(false);
        service.removeEntry("event-z", "entrant-z", unused -> successCalled.set(true), e -> fail("Unexpected failure: " + e.getMessage()));

        assertTrue(successCalled.get());
        assertEquals("event-z", repository.deleteEventId);
        assertEquals("entrant-z", repository.deleteEntrantUid);
    }
}
