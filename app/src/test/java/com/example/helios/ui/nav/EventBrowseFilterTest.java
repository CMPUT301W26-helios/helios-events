package com.example.helios.ui.nav;

import com.example.helios.model.Event;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class EventBrowseFilterTest {

    @Test
    public void apply_openNowFilter_keepsOnlyCurrentlyOpenEvents() {
        long now = System.currentTimeMillis();
        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.OPEN_NOW);

        Event openEvent = event("open", now - 1_000L, now + 1_000L, 25, null);
        Event upcomingEvent = event("upcoming", now + 50_000L, now + 80_000L, 25, null);
        Event closedEvent = event("closed", now - 80_000L, now - 50_000L, 25, null);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(openEvent, upcomingEvent, closedEvent),
                null,
                Collections.emptyMap(),
                ""
        );

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("open", result.getFilteredEvents().get(0).getEventId());
    }

    @Test
    public void apply_upcomingFilter_keepsOnlyEventsOpeningLater() {
        long now = System.currentTimeMillis();
        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.UPCOMING);

        Event openEvent = event("open", now - 1_000L, now + 1_000L, 25, null);
        Event upcomingEvent = event("upcoming", now + 50_000L, now + 80_000L, 25, null);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(openEvent, upcomingEvent),
                null,
                Collections.emptyMap(),
                ""
        );

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("upcoming", result.getFilteredEvents().get(0).getEventId());
    }

    @Test
    public void apply_limitedCapacityFilter_keepsOnlyCapacityLimitedEvents() {
        long now = System.currentTimeMillis();
        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setCapacityFilter(EventBrowseFilter.CapacityFilter.LIMITED_CAPACITY);

        Event limited = event("limited", now - 1_000L, now + 1_000L, 10, null);
        Event unlimited = event("unlimited", now - 1_000L, now + 1_000L, 0, null);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(unlimited, limited),
                null,
                Collections.emptyMap(),
                ""
        );

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("limited", result.getFilteredEvents().get(0).getEventId());
    }

    @Test
    public void apply_waitlistLimitedFilter_keepsOnlyWaitlistCappedEvents() {
        long now = System.currentTimeMillis();
        EventBrowseFilter filter = new EventBrowseFilter();
        filter.setCapacityFilter(EventBrowseFilter.CapacityFilter.WAITLIST_LIMITED);

        Event capped = event("capped", now - 1_000L, now + 1_000L, 20, 40);
        Event uncapped = event("uncapped", now - 1_000L, now + 1_000L, 20, null);

        EventBrowseFilter.Result result = filter.apply(
                Arrays.asList(capped, uncapped),
                null,
                Collections.emptyMap(),
                ""
        );

        assertEquals(1, result.getFilteredEvents().size());
        assertEquals("capped", result.getFilteredEvents().get(0).getEventId());
    }

    private Event event(
            String eventId,
            long registrationOpensMillis,
            long registrationClosesMillis,
            int capacity,
            Integer waitlistLimit
    ) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setTitle(eventId);
        event.setRegistrationOpensMillis(registrationOpensMillis);
        event.setRegistrationClosesMillis(registrationClosesMillis);
        event.setCapacity(capacity);
        event.setWaitlistLimit(waitlistLimit);
        event.setStartTimeMillis(registrationClosesMillis + 60_000L);
        return event;
    }
}
