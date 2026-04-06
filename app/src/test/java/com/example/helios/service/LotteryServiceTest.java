package com.example.helios.service;

import com.example.helios.data.EventRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class LotteryServiceTest {

    @Test
    public void runDraw_withNoWaitingEntrants_returnsFailureWithoutSavingEvent() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        OrganizerNotificationService organizerNotificationService = mock(OrganizerNotificationService.class);

        doAnswer(invocation -> {
            OnSuccessListener<java.util.List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(Collections.emptyList());
            return null;
        }).when(waitingListRepository).getAllWaitingListEntries(eq("event-1"), any(), any());

        LotteryService service = new LotteryService(waitingListRepository, eventRepository, organizerNotificationService);
        Event event = new Event();
        event.setEventId("event-1");

        AtomicReference<Exception> failure = new AtomicReference<>();
        service.runDraw(
                "organizer-1",
                event,
                1,
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertNotNull(failure.get());
        assertEquals(LotteryService.NO_PEOPLE_IN_EVENT_MESSAGE, failure.get().getMessage());
        verify(waitingListRepository).getAllWaitingListEntries(eq("event-1"), any(), any());
        verify(waitingListRepository, never()).upsertWaitingListEntry(any(), any(), any(), any(), any());
        verify(eventRepository, never()).saveEvent(any(), any(), any());
        verify(organizerNotificationService, never()).notifyDrawResults(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void runDraw_withZeroTargetCount_returnsFailureWithoutSavingEvent() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        OrganizerNotificationService organizerNotificationService = mock(OrganizerNotificationService.class);

        WaitingListEntry waitingEntry = new WaitingListEntry();
        waitingEntry.setEntrantUid("entrant-1");
        waitingEntry.setStatus(WaitingListStatus.WAITING);

        doAnswer(invocation -> {
            OnSuccessListener<java.util.List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(Collections.singletonList(waitingEntry));
            return null;
        }).when(waitingListRepository).getAllWaitingListEntries(eq("event-2"), any(), any());

        LotteryService service = new LotteryService(waitingListRepository, eventRepository, organizerNotificationService);
        Event event = new Event();
        event.setEventId("event-2");

        AtomicReference<Exception> failure = new AtomicReference<>();
        service.runDraw(
                "organizer-2",
                event,
                0,
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertNotNull(failure.get());
        assertEquals("Draw count must be greater than 0.", failure.get().getMessage());
        verify(waitingListRepository).getAllWaitingListEntries(eq("event-2"), any(), any());
        verify(waitingListRepository, never()).upsertWaitingListEntry(any(), any(), any(), any(), any());
        verify(eventRepository, never()).saveEvent(any(), any(), any());
        verify(organizerNotificationService, never()).notifyDrawResults(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void runDraw_incrementsDrawCountBeforeSavingEvent() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        OrganizerNotificationService organizerNotificationService = mock(OrganizerNotificationService.class);

        WaitingListEntry waitingEntry = new WaitingListEntry();
        waitingEntry.setEntrantUid("entrant-5");
        waitingEntry.setStatus(WaitingListStatus.WAITING);

        doAnswer(invocation -> {
            OnSuccessListener<java.util.List<WaitingListEntry>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(Collections.singletonList(waitingEntry));
            return null;
        }).when(waitingListRepository).getAllWaitingListEntries(eq("event-3"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
            onSuccess.onSuccess(null);
            return null;
        }).when(waitingListRepository).upsertWaitingListEntry(eq("event-3"), eq("entrant-5"), any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(eventRepository).saveEvent(any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(4);
            onSuccess.onSuccess(null);
            return null;
        }).when(organizerNotificationService).notifyDrawResults(any(), any(), any(), any(), any(), any());

        LotteryService service = new LotteryService(waitingListRepository, eventRepository, organizerNotificationService);
        Event event = new Event();
        event.setEventId("event-3");

        service.runDraw(
                "organizer-3",
                event,
                1,
                unused -> { },
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertEquals(1, event.getDrawCount());
        verify(eventRepository).saveEvent(eq(event), any(), any());
        verify(organizerNotificationService).notifyDrawResults(eq("organizer-3"), eq(event), any(), any(), any(), any());
    }
}
