package com.example.helios.service;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.example.helios.testutil.UnsafeTestHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventServiceTest {

    private static class FakeRepository extends FirebaseRepository {
        List<Event> allEventsToReturn = new ArrayList<>();
        String requestedEventId;
        Event eventToReturn;
        Event savedEvent;
        String deletedEventId;

        @Override
        public void getAllEvents(@androidx.annotation.NonNull OnSuccessListener<List<Event>> onSuccess,
                                 @androidx.annotation.NonNull OnFailureListener onFailure) {
            onSuccess.onSuccess(allEventsToReturn);
        }

        @Override
        public void getEventById(@androidx.annotation.NonNull String eventId,
                                 @androidx.annotation.NonNull OnSuccessListener<Event> onSuccess,
                                 @androidx.annotation.NonNull OnFailureListener onFailure) {
            requestedEventId = eventId;
            onSuccess.onSuccess(eventToReturn);
        }

        @Override
        public void saveEvent(@androidx.annotation.NonNull Event event,
                              @androidx.annotation.NonNull OnSuccessListener<Void> onSuccess,
                              @androidx.annotation.NonNull OnFailureListener onFailure) {
            savedEvent = event;
            onSuccess.onSuccess(null);
        }

        @Override
        public void deleteEvent(@androidx.annotation.NonNull String eventId,
                                @androidx.annotation.NonNull OnSuccessListener<Void> onSuccess,
                                @androidx.annotation.NonNull OnFailureListener onFailure) {
            deletedEventId = eventId;
            onSuccess.onSuccess(null);
        }
    }

    private EventService createServiceWithFakeRepository(FakeRepository repository) {
        EventService service = UnsafeTestHelper.allocateWithoutConstructor(EventService.class);
        UnsafeTestHelper.setObjectField(service, "repository", repository);
        return service;
    }

    @Test
    public void getAllEvents_returnsRepositoryEvents() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        repository.allEventsToReturn.add(new Event());
        repository.allEventsToReturn.add(new Event());

        EventService service = createServiceWithFakeRepository(repository);
        AtomicReference<List<Event>> result = new AtomicReference<>();

        service.getAllEvents(result::set, e -> fail("Unexpected failure: " + e.getMessage()));

        assertEquals(2, result.get().size());
    }

    @Test
    public void getEventById_passesIdAndReturnsRepositoryResult() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        Event expectedEvent = new Event();
        repository.eventToReturn = expectedEvent;

        EventService service = createServiceWithFakeRepository(repository);
        AtomicReference<Event> result = new AtomicReference<>();

        service.getEventById("event-123", result::set, e -> fail("Unexpected failure: " + e.getMessage()));

        assertEquals("event-123", repository.requestedEventId);
        assertSame(expectedEvent, result.get());
    }

    @Test
    public void saveEvent_passesSameEventToRepository() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        EventService service = createServiceWithFakeRepository(repository);

        Event event = new Event();
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.saveEvent(event, unused -> successCalled.set(true), e -> fail("Unexpected failure: " + e.getMessage()));

        assertTrue(successCalled.get());
        assertSame(event, repository.savedEvent);
    }

    @Test
    public void deleteEvent_passesEventIdToRepository() {
        FakeRepository repository = UnsafeTestHelper.allocateWithoutConstructor(FakeRepository.class);
        EventService service = createServiceWithFakeRepository(repository);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.deleteEvent("delete-me", unused -> successCalled.set(true), e -> fail("Unexpected failure: " + e.getMessage()));

        assertTrue(successCalled.get());
        assertEquals("delete-me", repository.deletedEventId);
    }
}
