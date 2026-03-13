package com.example.helios.service;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.testutil.UnsafeTestHelper;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class EntrantEventServiceTest {

    private static class FakeRepository extends FirebaseRepository {
        List<WaitingListEntry> entriesToReturn = new ArrayList<>();
        Exception failure;
        String requestedEventId;

        @Override
        public void getAllWaitingListEntries(
                @androidx.annotation.NonNull String eventId,
                @androidx.annotation.NonNull OnSuccessListener<List<WaitingListEntry>> onSuccess,
                @androidx.annotation.NonNull OnFailureListener onFailure
        ) {
            requestedEventId = eventId;
            if (failure != null) {
                onFailure.onFailure(failure);
            } else {
                onSuccess.onSuccess(entriesToReturn);
            }
        }
    }

    private EntrantEventService createServiceWithFakeRepository(FakeRepository repository) {
        EntrantEventService service = UnsafeTestHelper.allocateWithoutConstructor(EntrantEventService.class);
        UnsafeTestHelper.setObjectField(service, "repository", repository);
        return service;
    }

    private WaitingListEntry entryWithStatus(WaitingListStatus status) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setStatus(status);
        return entry;
    }

    @Test
    public void getFilledSlotsCount_countsOnlySelectedStatuses() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        repository.entriesToReturn = Arrays.asList(
                entryWithStatus(WaitingListStatus.WAITING),
                entryWithStatus(WaitingListStatus.INVITED),
                entryWithStatus(WaitingListStatus.ACCEPTED),
                entryWithStatus(WaitingListStatus.NOT_SELECTED),
                entryWithStatus(WaitingListStatus.CANCELLED),
                entryWithStatus(WaitingListStatus.DECLINED),
                entryWithStatus(null),
                null
        );

        EntrantEventService service = createServiceWithFakeRepository(repository);
        AtomicReference<Integer> count = new AtomicReference<>();

        service.getFilledSlotsCount("event-17", count::set, e -> fail("Unexpected failure: " + e.getMessage()));

        assertEquals("event-17", repository.requestedEventId);
        assertEquals(Integer.valueOf(3), count.get());
    }

    @Test
    public void getFilledSlotsCount_forwardsRepositoryFailure() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        RuntimeException expected = new RuntimeException("boom");
        repository.failure = expected;

        EntrantEventService service = createServiceWithFakeRepository(repository);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getFilledSlotsCount("event-22", count -> fail("Success should not be called"), failure::set);

        assertSame(expected, failure.get());
    }

    @Test
    public void getFilledSlotsCount_withEmptyEntries_returnsZero() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        repository.entriesToReturn = new ArrayList<>();
        EntrantEventService service = createServiceWithFakeRepository(repository);
        AtomicReference<Integer> count = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getFilledSlotsCount("event-empty", count::set, failure::set);

        assertEquals(Integer.valueOf(0), count.get());
        assertNull(failure.get());
    }
}
