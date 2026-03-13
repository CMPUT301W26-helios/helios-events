package com.example.helios.service;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.UserProfile;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ProfileServiceTest {

    private ProfileService createService() {
        return new ProfileService(
                mock(AuthDeviceService.class),
                mock(FirebaseRepository.class)
        );
    }

    @Test
    public void requiresProfileCompletion_returnsTrueWhenNameMissing() {
        ProfileService service = createService();
        UserProfile profile = new UserProfile("uid", null, "a@b.com", null, "user", true, "inst");

        assertTrue(service.requiresProfileCompletion(profile));
    }

    @Test
    public void requiresProfileCompletion_returnsTrueWhenEmailMissing() {
        ProfileService service = createService();
        UserProfile profile = new UserProfile("uid", "Alice", null, null, "user", true, "inst");

        assertTrue(service.requiresProfileCompletion(profile));
    }

    @Test
    public void requiresProfileCompletion_returnsFalseWhenNameAndEmailPresent() {
        ProfileService service = createService();
        UserProfile profile = new UserProfile("uid", "Alice", "a@b.com", null, "user", true, "inst");

        assertFalse(service.requiresProfileCompletion(profile));
    }

    @Test
    public void bootstrapResult_returnsSameProfileAndFlag() {
        UserProfile profile = new UserProfile(
                "uid_1",
                "Alice",
                "alice@example.com",
                null,
                "user",
                true,
                "installation_1"
        );

        ProfileService.BootstrapResult result = new ProfileService.BootstrapResult(profile, false);

        assertSame(profile, result.getProfile());
        assertFalse(result.isNewUser());
    }
}