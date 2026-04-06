package com.example.helios.service;

import com.example.helios.data.NotificationRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.data.WaitingListRepository;
import com.example.helios.model.Event;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
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

    private WaitingListEntry makeEntry(String eventId, String entrantUid, WaitingListStatus status) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEventId(eventId);
        entry.setEntrantUid(entrantUid);
        entry.setStatus(status);
        return entry;
    }

    @Test
    public void notifyCoOrganizerInvite_createsSingleNotificationRecord() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizerNotificationService service = new OrganizerNotificationService(waitingListRepository, notificationRepository, userRepository);
        Event event = makeEvent("event-1", "Dance Party", "organizer-1");

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(enabledUser("user-2"));
            return null;
        }).when(userRepository).getUser(eq("user-2"), any(), any());

        ArgumentCaptor<List<NotificationRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(notificationRepository).saveNotificationsBatch(recordsCaptor.capture(), any(), any());

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
        assertEquals("Co-organizer invite: Dance Party", record.getTitle());
        assertTrue(record.getMessage().contains("Dance Party"));
        assertTrue(record.getMessage().contains("accept or decline"));
        assertFalse(record.getNotificationId().trim().isEmpty());
    }

    @Test
    public void notifyPrivateEventInvite_createsSingleNotificationRecord() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizerNotificationService service = new OrganizerNotificationService(waitingListRepository, notificationRepository, userRepository);
        Event event = makeEvent("event-2", "Secret Show", "organizer-3");

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(enabledUser("user-4"));
            return null;
        }).when(userRepository).getUser(eq("user-4"), any(), any());

        ArgumentCaptor<List<NotificationRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(notificationRepository).saveNotificationsBatch(recordsCaptor.capture(), any(), any());

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
        assertEquals("Private event invite: Secret Show", record.getTitle());
        assertTrue(record.getMessage().contains("Secret Show"));
        assertTrue(record.getMessage().contains("accept or decline"));
        assertFalse(record.getNotificationId().trim().isEmpty());
    }

    @Test
    public void notifyDrawResults_createsActionableMessagesForEachAudience() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizerNotificationService service = new OrganizerNotificationService(
                waitingListRepository,
                notificationRepository,
                userRepository
        );
        Event event = makeEvent("event-7", "Harvest Social", "organizer-7");

        doAnswer(invocation -> {
            String uid = invocation.getArgument(0);
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(enabledUser(uid));
            return null;
        }).when(userRepository).getUser(any(), any(), any());

        ArgumentCaptor<List<NotificationRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(notificationRepository).saveNotificationsBatch(recordsCaptor.capture(), any(), any());

        service.notifyDrawResults(
                "organizer-7",
                event,
                java.util.Collections.singletonList(makeEntry("event-7", "user-invited", WaitingListStatus.INVITED)),
                java.util.Collections.singletonList(makeEntry("event-7", "user-waiting", WaitingListStatus.NOT_SELECTED)),
                unused -> {},
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        List<List<NotificationRecord>> savedBatches = recordsCaptor.getAllValues();
        assertEquals(2, savedBatches.size());

        NotificationRecord invited = savedBatches.get(0).get(0);
        assertEquals(NotificationAudience.INVITED, invited.getAudience());
        assertEquals("Invitation ready: Harvest Social", invited.getTitle());
        assertTrue(invited.getMessage().contains("selected in the latest draw"));
        assertTrue(invited.getMessage().contains("accept or decline"));

        NotificationRecord notSelected = savedBatches.get(1).get(0);
        assertEquals(NotificationAudience.NOT_SELECTED, notSelected.getAudience());
        assertEquals("Draw update: Harvest Social", notSelected.getTitle());
        assertTrue(notSelected.getMessage().contains("not selected"));
        assertTrue(notSelected.getMessage().contains("invite more entrants"));
    }

    @Test
    public void cancelEntrant_createsDetailedCancellationNotification() {
        WaitingListRepository waitingListRepository = mock(WaitingListRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrganizerNotificationService service = new OrganizerNotificationService(
                waitingListRepository,
                notificationRepository,
                userRepository
        );
        Event event = makeEvent("event-8", "Lantern Walk", "organizer-8");
        WaitingListEntry entry = makeEntry("event-8", "user-8", WaitingListStatus.INVITED);

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(3);
            onSuccess.onSuccess(null);
            return null;
        }).when(waitingListRepository).updateWaitingListEntry(eq("event-8"), eq("user-8"), any(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(enabledUser("user-8"));
            return null;
        }).when(userRepository).getUser(eq("user-8"), any(), any());

        ArgumentCaptor<List<NotificationRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(notificationRepository).saveNotificationsBatch(recordsCaptor.capture(), any(), any());

        service.cancelEntrant(
                "organizer-8",
                event,
                entry,
                "Capacity reduced",
                unused -> {},
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        NotificationRecord saved = recordsCaptor.getValue().get(0);
        assertEquals(NotificationAudience.CANCELLED, saved.getAudience());
        assertEquals("Registration cancelled: Lantern Walk", saved.getTitle());
        assertTrue(saved.getMessage().contains("Capacity reduced"));
        assertTrue(saved.getMessage().contains("Open the event"));
        assertEquals(WaitingListStatus.CANCELLED, entry.getStatus());
        assertTrue(entry.getCancelledAtMillis() > 0);
    }
}
