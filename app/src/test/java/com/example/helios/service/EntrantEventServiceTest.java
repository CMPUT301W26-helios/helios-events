package com.example.helios.service;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class EntrantEventServiceTest {

    private WaitingListEntry entryWithStatus(WaitingListStatus status) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setStatus(status);
        return entry;
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

        service.getFilledSlotsCount(
                "event-17",
                count::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertEquals(Integer.valueOf(3), count.get());
        verify(repository).getAllWaitingListEntries(eq("event-17"), any(), any());
        verifyNoInteractions(profileService);
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

        service.getFilledSlotsCount(
                "event-22",
                count -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository).getAllWaitingListEntries(eq("event-22"), any(), any());
        verifyNoInteractions(profileService);
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
        verifyNoInteractions(profileService);
    }
}