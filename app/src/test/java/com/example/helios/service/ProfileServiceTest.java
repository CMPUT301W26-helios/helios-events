package com.example.helios.service;

import com.example.helios.model.UserProfile;
import com.example.helios.testutil.UnsafeTestHelper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ProfileServiceTest {
    @Test
    public void requiresProfileCompletion_returnsTrueWhenNameMissing() {
        ProfileService service = UnsafeTestHelper.allocateWithoutConstructor(ProfileService.class);
        UserProfile profile = new UserProfile("uid", null, "a@b.com", null, "user", true, "inst");

        assertTrue(service.requiresProfileCompletion(profile));
    }

    @Test
    public void requiresProfileCompletion_returnsTrueWhenEmailMissing() {
        ProfileService service = UnsafeTestHelper.allocateWithoutConstructor(ProfileService.class);
        UserProfile profile = new UserProfile("uid", "Alice", null, null, "user", true, "inst");

        assertTrue(service.requiresProfileCompletion(profile));
    }

    @Test
    public void requiresProfileCompletion_returnsFalseWhenNameAndEmailPresent() {
        ProfileService service = UnsafeTestHelper.allocateWithoutConstructor(ProfileService.class);
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
