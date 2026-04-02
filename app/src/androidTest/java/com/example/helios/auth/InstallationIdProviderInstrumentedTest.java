package com.example.helios.auth;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InstallationIdProviderInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        InstallationIdProvider.clearInstallationId(context);
    }

    @After
    public void tearDown() {
        InstallationIdProvider.clearInstallationId(context);
    }

    @Test
    public void getInstallationId_returnsStableIdUntilCleared() {
        String first = InstallationIdProvider.getInstallationId(context);
        String second = InstallationIdProvider.getInstallationId(context);

        assertNotNull(first);
        assertFalse(first.trim().isEmpty());
        assertEquals(first, second);
    }

    @Test
    public void clearInstallationId_forcesNewValueOnNextRead() {
        String first = InstallationIdProvider.getInstallationId(context);

        InstallationIdProvider.clearInstallationId(context);
        String second = InstallationIdProvider.getInstallationId(context);

        assertNotNull(second);
        assertNotEquals(first, second);
    }
}
