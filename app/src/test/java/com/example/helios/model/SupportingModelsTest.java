package com.example.helios.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class SupportingModelsTest {

    @Test
    public void adminDevice_roundTripsFields() {
        AdminDevice device = new AdminDevice("install-abc", true, "Lab tablet", 1741996800000L);

        assertEquals("install-abc", device.getInstallationId());
        assertTrue(device.isEnabled());
        assertEquals("Lab tablet", device.getNote());
        assertEquals(1741996800000L, device.getAddedAtMillis());

        device.setEnabled(false);
        device.setNote("Disabled after policy violation");

        assertFalse(device.isEnabled());
        assertEquals("Disabled after policy violation", device.getNote());
    }

    @Test
    public void geoPoint_roundTripsCoordinates() {
        GeoPoint point = new GeoPoint(53.5461, -113.4938);

        assertEquals(53.5461, point.getLatitude(), 0.00001);
        assertEquals(-113.4938, point.getLongitude(), 0.00001);

        point.setLatitude(53.5444);
        point.setLongitude(-113.4909);

        assertEquals(53.5444, point.getLatitude(), 0.00001);
        assertEquals(-113.4909, point.getLongitude(), 0.00001);
    }

    @Test
    public void imageAsset_roundTripsFields() {
        ImageAsset imageAsset = new ImageAsset(
                "image-44",
                "organizer-11",
                "event-22",
                "posters/event-22/banner.jpg",
                1742083200000L
        );

        assertEquals("image-44", imageAsset.getImageId());
        assertEquals("organizer-11", imageAsset.getOwnerUid());
        assertEquals("event-22", imageAsset.getEventId());
        assertEquals("posters/event-22/banner.jpg", imageAsset.getStoragePath());
        assertEquals(1742083200000L, imageAsset.getUploadedAtMillis());
    }

    @Test
    public void notificationRecord_supportsIndividualAndGroupAudiences() {
        NotificationRecord record = new NotificationRecord(
                "notif-01",
                "event-33",
                "organizer-11",
                "user-99",
                NotificationAudience.INDIVIDUAL,
                "You were selected",
                "Please accept within 48 hours.",
                1742256000000L
        );

        assertEquals(NotificationAudience.INDIVIDUAL, record.getAudience());
        assertEquals("user-99", record.getRecipientUid());

        record.setAudience(NotificationAudience.SELECTED);
        record.setRecipientUid(null);

        assertEquals(NotificationAudience.SELECTED, record.getAudience());
        assertNull(record.getRecipientUid());
    }

    @Test
    public void waitingListEntry_roundTripsFields() {
        WaitingListEntry entry = new WaitingListEntry(
                "event-55",
                "entrant-77",
                WaitingListStatus.WAITING,
                1742342400000L
        );

        assertEquals("event-55", entry.getEventId());
        assertEquals("entrant-77", entry.getEntrantUid());
        assertEquals(WaitingListStatus.WAITING, entry.getStatus());
        assertEquals(1742342400000L, entry.getJoinedAtMillis());

        entry.setStatus(WaitingListStatus.INVITED);
        assertEquals(WaitingListStatus.INVITED, entry.getStatus());
    }

    @Test
    public void enums_exposeExpectedWorkflowStates() {
        assertNotNull(NotificationAudience.valueOf("INDIVIDUAL"));
        assertNotNull(NotificationAudience.valueOf("WAITING"));
        assertNotNull(NotificationAudience.valueOf("SELECTED"));
        assertNotNull(NotificationAudience.valueOf("CANCELLED"));

        assertNotNull(WaitingListStatus.valueOf("WAITING"));
        assertNotNull(WaitingListStatus.valueOf("INVITED"));
        assertNotNull(WaitingListStatus.valueOf("ACCEPTED"));
        assertNotNull(WaitingListStatus.valueOf("DECLINED"));
        assertNotNull(WaitingListStatus.valueOf("CANCELLED"));
        assertNotNull(WaitingListStatus.valueOf("NOT_SELECTED"));
    }
}
