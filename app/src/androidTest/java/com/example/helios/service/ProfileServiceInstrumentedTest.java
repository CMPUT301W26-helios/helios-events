package com.example.helios.service;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.helios.model.UserProfile;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProfileServiceInstrumentedTest {

    @Test
    public void requiresProfileCompletion_returnsTrueWhenNameOrEmailMissing() {
        ProfileService service = new ProfileService();
        UserProfile profile = new UserProfile();

        profile.setName("Alice Somelastname");
        profile.setEmail(null);
        assertTrue(service.requiresProfileCompletion(profile));

        profile.setEmail("alice@example.com");
        assertFalse(service.requiresProfileCompletion(profile));

        profile.setName("   ");
        assertTrue(service.requiresProfileCompletion(profile));
    }
}
