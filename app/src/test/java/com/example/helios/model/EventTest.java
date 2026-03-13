package com.example.helios.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class EventTest {

    @Test
    public void constructor_setsAllFields_andDefaultsDrawToFalse() {
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
                "helios://event/event-1001"
        );

        assertEquals("event-1001", event.getEventId());
        assertEquals("Beginner Swim Lessons", event.getTitle());
        assertEquals("Six-week evening swim program for beginners.", event.getDescription());
        assertEquals("Sports Centre", event.getLocationName());
        assertEquals("420 Fake Street NW, Edmonton, AB", event.getAddress());
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
        assertFalse(event.isDrawHappened());
    }

    @Test
    public void defaultConstructor_startsWithJavaDefaultValues() {
        Event event = new Event();

        assertNull(event.getEventId());
        assertNull(event.getTitle());
        assertNull(event.getDescription());
        assertNull(event.getLocationName());
        assertNull(event.getAddress());
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
        assertFalse(event.isDrawHappened());
    }

    @Test
    public void setters_roundTripUpdatedValues() {
        Event event = new Event();

        event.setEventId("event-2002");
        event.setTitle("Dance Lessons");
        event.setDescription("Learn how to dance (for beginners!)");
        event.setLocationName("Downtown Community Centre");
        event.setAddress("420 Fake Street NW, Edmonton, AB");
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
        event.setDrawHappened(true);

        assertEquals("event-2002", event.getEventId());
        assertEquals("Dance Lessons", event.getTitle());
        assertEquals("Learn how to dance (for beginners!)", event.getDescription());
        assertEquals("Downtown Community Centre", event.getLocationName());
        assertEquals("420 Fake Street NW, Edmonton, AB", event.getAddress());
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
        assertTrue(event.isDrawHappened());
    }

    @Test
    public void setWaitlistLimit_acceptsNonNullInteger() {
        Event event = new Event();

        event.setWaitlistLimit(120);

        assertEquals(Integer.valueOf(120), event.getWaitlistLimit());
    }
}