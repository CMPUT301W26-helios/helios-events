package com.example.helios.service;

import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OrganizerNotificationServiceTest {

    private Event makeEvent(String eventId, String title, String organizerUid) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setTitle(title);
        event.setOrganizerUid(organizerUid);
        return event;
    }

    private UserProfile enabledUser(String uid) {
        UserProfile user = new UserProfile();
        user.setUid(uid);
        user.setNotificationsEnabled(true);
        return user;
    }

    @Test
    public void notifyCoOrganizerInvite_createsSingleNotificationRecord() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        OrganizerNotificationService service = new OrganizerNotificationService(repository);
        Event event = makeEvent("event-1", "Dance Party", "organizer-1");

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(enabledUser("user-2"));
            return null;
        }).when(repository).getUser(eq("user-2"), any(), any());

        ArgumentCaptor<List<NotificationRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).saveNotificationsBatch(recordsCaptor.capture(), any(), any());

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        service.notifyCoOrganizerInvite(
                "organizer-1",
                event,
                "user-2",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertNotNull(result.get());
        assertEquals(1, result.get().getRecipientCount());
        List<NotificationRecord> saved = recordsCaptor.getValue();
        assertEquals(1, saved.size());
        NotificationRecord record = saved.get(0);
        assertEquals(NotificationAudience.CO_ORGANIZER_INVITE, record.getAudience());
        assertEquals("event-1", record.getEventId());
        assertEquals("organizer-1", record.getSenderUid());
        assertEquals("user-2", record.getRecipientUid());
        assertEquals("Co-organizer invitation", record.getTitle());
        assertTrue(record.getMessage().contains("Dance Party"));
        assertFalse(record.getNotificationId().trim().isEmpty());
    }

    @Test
    public void notifyPrivateEventInvite_createsSingleNotificationRecord() {
        FirebaseRepository repository = mock(FirebaseRepository.class);
        OrganizerNotificationService service = new OrganizerNotificationService(repository);
        Event event = makeEvent("event-2", "Secret Show", "organizer-3");

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(enabledUser("user-4"));
            return null;
        }).when(repository).getUser(eq("user-4"), any(), any());

        ArgumentCaptor<List<NotificationRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).saveNotificationsBatch(recordsCaptor.capture(), any(), any());

        AtomicReference<NotificationSendResult> result = new AtomicReference<>();
        service.notifyPrivateEventInvite(
                "organizer-3",
                event,
                "user-4",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertNotNull(result.get());
        assertEquals(1, result.get().getRecipientCount());
        List<NotificationRecord> saved = recordsCaptor.getValue();
        assertEquals(1, saved.size());
        NotificationRecord record = saved.get(0);
        assertEquals(NotificationAudience.PRIVATE_EVENT_INVITE, record.getAudience());
        assertEquals("event-2", record.getEventId());
        assertEquals("organizer-3", record.getSenderUid());
        assertEquals("user-4", record.getRecipientUid());
        assertEquals("Private event invitation", record.getTitle());
        assertTrue(record.getMessage().contains("Secret Show"));
        assertFalse(record.getNotificationId().trim().isEmpty());
    }
}
