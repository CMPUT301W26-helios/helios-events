package com.example.helios.service;

import com.example.helios.model.UserProfile;

import org.junit.Test;

import static org.junit.Assert.*;

public class ProfileServiceBootstrapResultTest {

    @Test
    public void bootstrapResult_exposesProfileAndNewUserFlag() {
        UserProfile profile = new UserProfile(
                "uid-boot-test",
                "Aman Ofnoimportance",
                "amanofnoimport@example.com",
                "780-123-4567",
                "user",
                true,
                "dummy-install-id"
        );

        ProfileService.BootstrapResult result = new ProfileService.BootstrapResult(profile, true);

        assertSame(profile, result.getProfile());
        assertTrue(result.isNewUser());
    }
}
