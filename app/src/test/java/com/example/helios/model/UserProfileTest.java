package com.example.helios.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class UserProfileTest {

    @Test
    public void setters_trimAndNormalizeBlankStringsToNull() {
        UserProfile profile = new UserProfile();

        profile.setName("  Alex Lastname  ");
        profile.setEmail("  alex@example.com  ");
        profile.setPhone("   ");

        assertEquals("Alex Lastname", profile.getName());
        assertEquals("alex@example.com", profile.getEmail());
        assertNull(profile.getPhone());
    }

    @Test
    public void constructor_preservesRawValuesWhileSettersNormalize() {
        UserProfile profile = new UserProfile(
                "uid-123",
                "   ",
                "   ",
                "   ",
                "user",
                true,
                "install-1"
        );

        assertEquals("   ", profile.getName());
        assertEquals("   ", profile.getEmail());
        assertEquals("   ", profile.getPhone());
        assertFalse(profile.hasRequiredProfileInfo());
        assertNull(profile.getDisplayNameOrFallback());
    }

    @Test
    public void hasRequiredProfileInfo_requiresNonBlankNameAndEmail() {
        UserProfile profile = new UserProfile();

        profile.setName("Tammy");
        assertFalse(profile.hasRequiredProfileInfo());

        profile.setEmail("tammy@example.com");
        assertTrue(profile.hasRequiredProfileInfo());

        profile.setName("   ");
        assertFalse(profile.hasRequiredProfileInfo());
    }

    @Test
    public void isAdmin_returnsTrueOnlyForExactAdminRole() {
        UserProfile profile = new UserProfile();

        profile.setRole("admin");
        assertTrue(profile.isAdmin());

        profile.setRole("user");
        assertFalse(profile.isAdmin());

        profile.setRole(null);
        assertFalse(profile.isAdmin());

        profile.setRole("Admin");
        assertFalse(profile.isAdmin());
    }

    @Test
    public void getDisplayNameOrFallback_returnsNameOnlyWhenNonBlank() {
        UserProfile profile = new UserProfile();

        assertNull(profile.getDisplayNameOrFallback());

        profile.setName("   ");
        assertNull(profile.getDisplayNameOrFallback());

        profile.setName("Morgana Le Fay");
        assertEquals("Morgana Le Fay", profile.getDisplayNameOrFallback());
    }
}
