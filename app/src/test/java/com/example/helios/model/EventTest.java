package com.example.helios.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class EventTest {

    private final List<String> interests = Arrays.asList("Tag1", "Tag2");

    @Test
    public void constructor_setsAllFields_andUsesProvidedDrawCount() {
        Event event = new Event(
                "event-1001",
                "Beginner Swim Lessons",
                "Six-week evening swim program for beginners.",
                "Sports Centre",
                "420 Fake Street NW, Edmonton, AB",
                1743472800000L,
                1743476400000L,
                1740787200000L,
                1741392000000L,
                20,
                20,
                80,
                true,
                "Lottery draw occurs after registration closes.",
                "organizer-55",
                "poster-900",
                "helios://event/event-1001",
                interests,
                2,
                true
        );

        assertEquals("event-1001", event.getEventId());
        assertEquals("Beginner Swim Lessons", event.getTitle());
        assertEquals("Six-week evening swim program for beginners.", event.getDescription());
        assertEquals("Sports Centre", event.getLocationName());
        assertEquals("420 Fake Street NW, Edmonton, AB", event.getAddress());
        assertNull(event.getGeofenceCenter());
        assertNull(event.getGeofenceRadiusMeters());
        assertEquals(1743472800000L, event.getStartTimeMillis());
        assertEquals(1743476400000L, event.getEndTimeMillis());
        assertEquals(1740787200000L, event.getRegistrationOpensMillis());
        assertEquals(1741392000000L, event.getRegistrationClosesMillis());
        assertEquals(20, event.getCapacity());
        assertEquals(20, event.getSampleSize());
        assertEquals(Integer.valueOf(80), event.getWaitlistLimit());
        assertTrue(event.isGeolocationRequired());
        assertEquals("Lottery draw occurs after registration closes.", event.getLotteryGuidelines());
        assertEquals("organizer-55", event.getOrganizerUid());
        assertEquals("poster-900", event.getPosterImageId());
        assertEquals("helios://event/event-1001", event.getQrCodeValue());
        assertEquals(interests, event.getInterests());
        assertEquals(2, event.getDrawCount());
        assertTrue(event.isDrawHappened());
    }

    @Test
    public void defaultConstructor_startsWithJavaDefaultValues() {
        Event event = new Event();

        assertNull(event.getEventId());
        assertNull(event.getTitle());
        assertNull(event.getDescription());
        assertNull(event.getLocationName());
        assertNull(event.getAddress());
        assertNull(event.getGeofenceCenter());
        assertNull(event.getGeofenceRadiusMeters());
        assertEquals(0L, event.getStartTimeMillis());
        assertEquals(0L, event.getEndTimeMillis());
        assertEquals(0L, event.getRegistrationOpensMillis());
        assertEquals(0L, event.getRegistrationClosesMillis());
        assertEquals(0, event.getCapacity());
        assertEquals(0, event.getSampleSize());
        assertNull(event.getWaitlistLimit());
        assertFalse(event.isGeolocationRequired());
        assertNull(event.getLotteryGuidelines());
        assertNull(event.getOrganizerUid());
        assertNull(event.getPosterImageId());
        assertNull(event.getQrCodeValue());
        assertNull(event.getInterests());
        assertEquals(0, event.getDrawCount());
        assertFalse(event.isDrawHappened());
    }

    @Test
    public void setters_roundTripUpdatedValues() {
        Event event = new Event();
        List<String> updatedInterests = Arrays.asList("Dance", "Beginners");

        event.setEventId("event-2002");
        event.setTitle("Dance Lessons");
        event.setDescription("Learn how to dance (for beginners!)");
        event.setLocationName("Downtown Community Centre");
        event.setAddress("420 Fake Street NW, Edmonton, AB");
        event.setGeofenceCenter(53.5461, -113.4938);
        event.setGeofenceRadiusMeters(250);
        event.setStartTimeMillis(1751328000000L);
        event.setEndTimeMillis(1751335200000L);
        event.setRegistrationOpensMillis(1750118400000L);
        event.setRegistrationClosesMillis(1750809600000L);
        event.setCapacity(60);
        event.setSampleSize(25);
        event.setWaitlistLimit(null);
        event.setGeolocationRequired(false);
        event.setLotteryGuidelines("A redraw occurs when invitees decline.");
        event.setOrganizerUid("organizer-77");
        event.setPosterImageId(null);
        event.setQrCodeValue("qr://dance-lessons");
        event.setInterests(updatedInterests);
        event.setDrawCount(3);

        assertEquals("event-2002", event.getEventId());
        assertEquals("Dance Lessons", event.getTitle());
        assertEquals("Learn how to dance (for beginners!)", event.getDescription());
        assertEquals("Downtown Community Centre", event.getLocationName());
        assertEquals("420 Fake Street NW, Edmonton, AB", event.getAddress());
        assertNotNull(event.getGeofenceCenter());
        assertEquals(53.5461, event.getGeofenceCenter().getLatitude(), 0.0001);
        assertEquals(-113.4938, event.getGeofenceCenter().getLongitude(), 0.0001);
        assertEquals(Integer.valueOf(250), event.getGeofenceRadiusMeters());
        assertTrue(event.hasGeofence());
        assertEquals(1751328000000L, event.getStartTimeMillis());
        assertEquals(1751335200000L, event.getEndTimeMillis());
        assertEquals(1750118400000L, event.getRegistrationOpensMillis());
        assertEquals(1750809600000L, event.getRegistrationClosesMillis());
        assertEquals(60, event.getCapacity());
        assertEquals(25, event.getSampleSize());
        assertNull(event.getWaitlistLimit());
        assertFalse(event.isGeolocationRequired());
        assertEquals("A redraw occurs when invitees decline.", event.getLotteryGuidelines());
        assertEquals("organizer-77", event.getOrganizerUid());
        assertNull(event.getPosterImageId());
        assertEquals("qr://dance-lessons", event.getQrCodeValue());
        assertEquals(updatedInterests, event.getInterests());
        assertEquals(3, event.getDrawCount());
        assertTrue(event.isDrawHappened());
    }

    @Test
    public void setWaitlistLimit_acceptsNonNullInteger() {
        Event event = new Event();

        event.setWaitlistLimit(120);

        assertEquals(Integer.valueOf(120), event.getWaitlistLimit());
    }

    @Test
    public void coOrganizerHelpers_reportAcceptedAndPendingUsers() {
        Event event = new Event();

        event.setCoOrganizerUids(Arrays.asList("co-1", "co-2"));
        event.setPendingCoOrganizerUids(Arrays.asList("pending-1"));

        assertTrue(event.isCoOrganizer("co-1"));
        assertFalse(event.isCoOrganizer("pending-1"));
        assertTrue(event.isPendingCoOrganizer("pending-1"));
        assertFalse(event.isPendingCoOrganizer("co-2"));
    }

    @Test
    public void legacyDrawHappenedFlag_stillMarksEventAsDrawn() {
        Event event = new Event();

        event.setDrawHappened(true);

        assertEquals(0, event.getDrawCount());
        assertTrue(event.isDrawHappened());
    }

    @Test
    public void constructor_reportsDrawnWhenDrawCountIsPositive() {
        Event event = new Event(
                "event-3003",
                "Yoga Class",
                "Morning yoga session.",
                "Wellness Studio",
                "101 Example Ave, Edmonton, AB",
                1760000000000L,
                1760003600000L,
                1759000000000L,
                1759500000000L,
                15,
                10,
                null,
                false,
                "Lottery closes before the class starts.",
                "organizer-99",
                null,
                null,
                Arrays.asList("Wellness", "Fitness"),
                1,
                true
        );

        assertEquals(1, event.getDrawCount());
        assertTrue(event.isDrawHappened());
    }

    @Test
    public void hasGeofence_requiresCenterAndPositiveRadius() {
        Event event = new Event();

        assertFalse(event.hasGeofence());

        event.setGeofenceCenter(53.5461, -113.4938);
        assertFalse(event.hasGeofence());

        event.setGeofenceRadiusMeters(0);
        assertFalse(event.hasGeofence());

        event.setGeofenceRadiusMeters(150);
        assertTrue(event.hasGeofence());
    }
}
