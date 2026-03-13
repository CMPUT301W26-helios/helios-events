package com.example.helios.service;

import android.content.Context;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class ProfileServiceTest {

    private ProfileService createService(
            AuthDeviceService authDeviceService,
            FirebaseRepository repository,
            ProfileService.InstallationIdSource installationIdSource
    ) {
        return new ProfileService(authDeviceService, repository, installationIdSource);
    }

    private FirebaseUser mockUser(String uid) {
        FirebaseUser user = mock(FirebaseUser.class);
        doReturn(uid).when(user).getUid();
        return user;
    }

    private void stubSignedInUser(AuthDeviceService authDeviceService, FirebaseUser user) {
        doAnswer(invocation -> {
            OnSuccessListener<FirebaseUser> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(user);
            return null;
        }).when(authDeviceService).ensureSignedIn(any(), any());
    }

    @Test
    public void requiresProfileCompletion_returnsTrueWhenNameMissing() {
        ProfileService service = createService(
                mock(AuthDeviceService.class),
                mock(FirebaseRepository.class),
                context -> "inst"
        );
        UserProfile profile = new UserProfile("uid", null, "a@b.com", null, "user", true, "inst");

        assertTrue(service.requiresProfileCompletion(profile));
    }

    @Test
    public void requiresProfileCompletion_returnsTrueWhenEmailMissing() {
        ProfileService service = createService(
                mock(AuthDeviceService.class),
                mock(FirebaseRepository.class),
                context -> "inst"
        );
        UserProfile profile = new UserProfile("uid", "Alice", null, null, "user", true, "inst");

        assertTrue(service.requiresProfileCompletion(profile));
    }

    @Test
    public void requiresProfileCompletion_returnsFalseWhenNameAndEmailPresent() {
        ProfileService service = createService(
                mock(AuthDeviceService.class),
                mock(FirebaseRepository.class),
                context -> "inst"
        );
        UserProfile profile = new UserProfile("uid", "Alice", "a@b.com", null, "user", true, "inst");

        assertFalse(service.requiresProfileCompletion(profile));
    }

    @Test
    public void bootstrapCurrentUser_createsDefaultProfileWhenMissing() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        FirebaseUser user = mockUser("uid-new");
        stubSignedInUser(auth, user);

        Context context = mock(Context.class);

        doAnswer(invocation -> {
            OnSuccessListener<Boolean> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(false);
            return null;
        }).when(repository).isAdminInstallation(eq("inst-new"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).getUser(eq("uid-new"), any(), any());

        ArgumentCaptor<UserProfile> savedProfileCaptor = ArgumentCaptor.forClass(UserProfile.class);

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).saveUser(savedProfileCaptor.capture(), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst-new");
        AtomicReference<ProfileService.BootstrapResult> result = new AtomicReference<>();

        service.bootstrapCurrentUser(
                context,
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        UserProfile saved = savedProfileCaptor.getValue();
        assertEquals("uid-new", saved.getUid());
        assertNull(saved.getName());
        assertNull(saved.getEmail());
        assertNull(saved.getPhone());
        assertEquals("user", saved.getRole());
        assertTrue(saved.isNotificationsEnabled());
        assertEquals("inst-new", saved.getInstallationId());

        assertTrue(result.get().isNewUser());
        assertSame(saved, result.get().getProfile());
    }

    @Test
    public void bootstrapCurrentUser_returnsExistingProfileWithoutUpdateWhenAlreadyCorrect() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        FirebaseUser user = mockUser("uid-existing");
        stubSignedInUser(auth, user);

        UserProfile existing = new UserProfile(
                "uid-existing",
                "Alice",
                "alice@example.com",
                null,
                "user",
                true,
                "inst-ok"
        );

        doAnswer(invocation -> {
            OnSuccessListener<Boolean> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(false);
            return null;
        }).when(repository).isAdminInstallation(eq("inst-ok"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(existing);
            return null;
        }).when(repository).getUser(eq("uid-existing"), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst-ok");
        AtomicReference<ProfileService.BootstrapResult> result = new AtomicReference<>();

        service.bootstrapCurrentUser(
                mock(Context.class),
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(existing, result.get().getProfile());
        assertFalse(result.get().isNewUser());
        verify(repository, never()).updateUser(any(), any(), any());
    }

    @Test
    public void bootstrapCurrentUser_updatesRoleAndInstallationWhenNeeded() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        FirebaseUser user = mockUser("uid-update");
        stubSignedInUser(auth, user);

        UserProfile existing = new UserProfile(
                "uid-update",
                "Avery",
                "avery@example.com",
                null,
                "user",
                true,
                "old-inst"
        );

        doAnswer(invocation -> {
            OnSuccessListener<Boolean> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(true);
            return null;
        }).when(repository).isAdminInstallation(eq("new-inst"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(existing);
            return null;
        }).when(repository).getUser(eq("uid-update"), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).updateUser(eq(existing), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "new-inst");
        AtomicReference<ProfileService.BootstrapResult> result = new AtomicReference<>();

        service.bootstrapCurrentUser(
                mock(Context.class),
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertEquals("admin", existing.getRole());
        assertEquals("new-inst", existing.getInstallationId());
        assertSame(existing, result.get().getProfile());
        assertFalse(result.get().isNewUser());
        verify(repository).updateUser(eq(existing), any(), any());
    }

    @Test
    public void bootstrapCurrentUser_forwardsAuthFailure() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        RuntimeException expected = new RuntimeException("auth failure");

        doAnswer(invocation -> {
            OnFailureListener onFailure = invocation.getArgument(1);
            onFailure.onFailure(expected);
            return null;
        }).when(auth).ensureSignedIn(any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicReference<Exception> failure = new AtomicReference<>();

        service.bootstrapCurrentUser(
                mock(Context.class),
                result -> fail("Success should not be called"),
                failure::set
        );

        assertSame(expected, failure.get());
        verify(repository, never()).isAdminInstallation(any(), any(), any());
    }

    @Test
    public void ensureSignedIn_delegatesToAuthDeviceService() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        FirebaseUser expectedUser = mockUser("uid-auth");

        doAnswer(invocation -> {
            OnSuccessListener<FirebaseUser> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(expectedUser);
            return null;
        }).when(auth).ensureSignedIn(any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicReference<FirebaseUser> result = new AtomicReference<>();

        service.ensureSignedIn(
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(expectedUser, result.get());
        verify(auth).ensureSignedIn(any(), any());
    }

    @Test
    public void completeCurrentProfile_updatesFieldsAndPersistsProfile() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService service = spy(createService(auth, repository, ctx -> "inst"));
        Context context = mock(Context.class);

        UserProfile bootstrapped = new UserProfile(
                "uid-complete",
                null,
                null,
                null,
                "user",
                true,
                "inst"
        );

        doAnswer(invocation -> {
            OnSuccessListener<ProfileService.BootstrapResult> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(new ProfileService.BootstrapResult(bootstrapped, false));
            return null;
        }).when(service).bootstrapCurrentUser(eq(context), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).updateUser(eq(bootstrapped), any(), any());

        AtomicReference<UserProfile> result = new AtomicReference<>();

        service.completeCurrentProfile(
                context,
                "Alice",
                "alice@example.com",
                "780-555-1111",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(bootstrapped, result.get());
        assertEquals("Alice", bootstrapped.getName());
        assertEquals("alice@example.com", bootstrapped.getEmail());
        assertEquals("780-555-1111", bootstrapped.getPhone());
        verify(repository).updateUser(eq(bootstrapped), any(), any());
    }

    @Test
    public void loadCurrentProfile_returnsBootstrappedProfile() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        ProfileService service = spy(createService(auth, repository, ctx -> "inst"));
        Context context = mock(Context.class);

        UserProfile profile = new UserProfile(
                "uid-load",
                "Sam",
                "sam@example.com",
                null,
                "user",
                true,
                "inst"
        );

        doAnswer(invocation -> {
            OnSuccessListener<ProfileService.BootstrapResult> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(new ProfileService.BootstrapResult(profile, false));
            return null;
        }).when(service).bootstrapCurrentUser(eq(context), any(), any());

        AtomicReference<UserProfile> result = new AtomicReference<>();

        service.loadCurrentProfile(
                context,
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(profile, result.get());
    }

    @Test
    public void getUserProfile_delegatesToRepository() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        UserProfile expected = new UserProfile(
                "uid-target",
                "Dana",
                "dana@example.com",
                null,
                "user",
                true,
                "inst"
        );

        doAnswer(invocation -> {
            OnSuccessListener<UserProfile> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(expected);
            return null;
        }).when(repository).getUser(eq("uid-target"), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicReference<UserProfile> result = new AtomicReference<>();

        service.getUserProfile(
                "uid-target",
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(expected, result.get());
        verify(repository).getUser(eq("uid-target"), any(), any());
    }

    @Test
    public void getAllProfiles_delegatesToRepository() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);

        List<UserProfile> expected = Arrays.asList(
                new UserProfile("u1", "A", "a@x.com", null, "user", true, "i1"),
                new UserProfile("u2", "B", "b@x.com", null, "admin", true, "i2")
        );

        doAnswer(invocation -> {
            OnSuccessListener<List<UserProfile>> onSuccess = invocation.getArgument(0);
            onSuccess.onSuccess(expected);
            return null;
        }).when(repository).getAllUsers(any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicReference<List<UserProfile>> result = new AtomicReference<>();

        service.getAllProfiles(
                result::set,
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertSame(expected, result.get());
        verify(repository).getAllUsers(any(), any());
    }

    @Test
    public void deleteProfile_delegatesToRepository() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).deleteUser(eq("uid-delete"), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.deleteProfile(
                "uid-delete",
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).deleteUser(eq("uid-delete"), any(), any());
    }

    @Test
    public void deleteCurrentProfile_usesSignedInUid() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        FirebaseUser user = mockUser("uid-current-delete");
        stubSignedInUser(auth, user);

        doAnswer(invocation -> {
            OnSuccessListener<Void> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(null);
            return null;
        }).when(repository).deleteUser(eq("uid-current-delete"), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.deleteCurrentProfile(
                mock(Context.class),
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).deleteUser(eq("uid-current-delete"), any(), any());
    }

    @Test
    public void setNotificationsMuted_usesSignedInUidAndMutedValue() {
        AuthDeviceService auth = mock(AuthDeviceService.class);
        FirebaseRepository repository = mock(FirebaseRepository.class);
        FirebaseUser user = mockUser("uid-muted");
        stubSignedInUser(auth, user);

        doAnswer(invocation -> {
            String uid = invocation.getArgument(0);
            boolean muted = invocation.getArgument(1);
            OnSuccessListener<Void> onSuccess = invocation.getArgument(2);

            assertEquals("uid-muted", uid);
            assertTrue(muted);

            onSuccess.onSuccess(null);
            return null;
        }).when(repository).setNotificationsMuted(eq("uid-muted"), eq(true), any(), any());

        ProfileService service = createService(auth, repository, ctx -> "inst");
        AtomicBoolean successCalled = new AtomicBoolean(false);

        service.setNotificationsMuted(
                mock(Context.class),
                true,
                unused -> successCalled.set(true),
                e -> fail("Unexpected failure: " + e.getMessage())
        );

        assertTrue(successCalled.get());
        verify(repository).setNotificationsMuted(eq("uid-muted"), eq(true), any(), any());
    }
}