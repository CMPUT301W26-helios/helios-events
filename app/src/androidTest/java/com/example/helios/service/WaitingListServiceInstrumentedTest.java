package com.example.helios.service;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WaitingListServiceInstrumentedTest {

    @Test
    public void updateEntry_rejectsBlankEntrantUidBeforeRepositoryCall() throws Exception {
        WaitingListService service = new WaitingListService();
        WaitingListEntry entry = new WaitingListEntry();
        entry.setEventId("event-blank-uid");
        entry.setEntrantUid("     ");
        entry.setStatus(WaitingListStatus.WAITING);
        entry.setJoinedAtMillis(1740000000000L);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> success = new AtomicReference<>(false);

        service.updateEntry(
                "event-blank-uid",
                entry,
                unused -> {
                    success.set(true);
                    latch.countDown();
                },
                throwable -> {
                    failure.set(throwable);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(success.get());
        assertNotNull(failure.get());
        assertTrue(failure.get() instanceof IllegalArgumentException);
        assertEquals("entrantUid must not be empty.", failure.get().getMessage());
    }
}
