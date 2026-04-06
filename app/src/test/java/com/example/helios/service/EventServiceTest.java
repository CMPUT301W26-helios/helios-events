package com.example.helios.service;

import com.example.helios.data.EventRepository;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class EventServiceTest {

    @Test
    public void getAllEvents_returnsRepositoryEvents() {
        EventRepository repository = mock(EventRepository.class);

        List<Event> expectedEvents = new ArrayList<>();
        expectedEvents.add(new Event());
        expectedEvents.add(new Event());

        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(expectedEvents);
            return null;
        }).when(repository).getAllEvents(any(), any());

        EventService service = new EventService(repository);
        AtomicReference<List<Event>> result = new AtomicReference<>();

        service.getAllEvents(
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertEquals(2, result.get().size());
        assertSame(expectedEvents, result.get());
        verify(repository).getAllEvents(any(), any());
    }

    @Test
    public void getAllEvents_forwardsRepositoryFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        RuntimeException expected = new RuntimeException("getAllEvents failed");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(1);
            onFailure.onFailure(expected);
            return null;
        }).when(repository).getAllEvents(any(), any());

        EventService service = new EventService(repository);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getAllEvents(
                events -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository).getAllEvents(any(), any());
    }

    @Test
    public void getEventById_passesIdAndReturnsRepositoryResult() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        Event expectedEvent = new Event();

        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            OnSuccessListener<Event> onSuccess = invocation.getArgument(1);
            assertEquals("event-123", eventId);
            onSuccess.onSuccess(expectedEvent);
            return null;
        }).when(repository).getEventById(eq("event-123"), any(), any());

        EventService service = new EventService(repository);
        AtomicReference<Event> result = new AtomicReference<>();

        service.getEventById(
                "event-123",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(expectedEvent, result.get());
        verify(repository).getEventById(eq("event-123"), any(), any());
    }

    @Test
    public void getEventById_forwardsRepositoryFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        RuntimeException expected = new RuntimeException("boom");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(2);
            onFailure.onFailure(expected);
            return null;
        }).when(repository).getEventById(eq("event-404"), any(), any());

        EventService service = new EventService(repository);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.getEventById(
                "event-404",
                event -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository).getEventById(eq("event-404"), any(), any());
    }

    @Test
    public void saveEvent_passesSameEventToRepository() {
        FirebaseRepository repository = mock(FirebaseRepository.class);

        doAnswer(invocation -> {
            Event eventArg = invocation.getArgument(0);
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).saveEvent(any(Event.class), any(), any());

        EventService service = new EventService(repository);

        Event event = new Event();
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.saveEvent(
                event,
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).saveEvent(eq(event), any(), any());
    }

    @Test
    public void saveEvent_forwardsRepositoryFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        RuntimeException expected = new RuntimeException("save failed");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(2);
            onFailure.onFailure(expected);
            return null;
        }).when(repository).saveEvent(any(Event.class), any(), any());

        EventService service = new EventService(repository);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.saveEvent(
                new Event(),
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository).saveEvent(any(Event.class), any(), any());
    }

    @Test
    public void deleteEvent_passesEventIdToRepository() {
        FirebaseRepository repository = mock(FirebaseRepository.class);

        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            assertEquals("delete-me", eventId);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).deleteEvent(eq("delete-me"), any(), any());

        EventService service = new EventService(repository);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.deleteEvent(
                "delete-me",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).deleteEvent(eq("delete-me"), any(), any());
    }

    @Test
    public void deleteEvent_forwardsRepositoryFailure() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        RuntimeException expected = new RuntimeException("delete failed");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(2);
            onFailure.onFailure(expected);
            return null;
        }).when(repository).deleteEvent(eq("delete-me"), any(), any());

        EventService service = new EventService(repository);
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.deleteEvent(
                "delete-me",
                unused -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository).deleteEvent(eq("delete-me"), any(), any());
    }
}
