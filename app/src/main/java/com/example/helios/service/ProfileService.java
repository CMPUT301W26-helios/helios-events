package com.example.helios.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.auth.InstallationIdProvider;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

public class ProfileService {
    public static class BootstrapResult {
        private final UserProfile profile;
        private final boolean isNewUser;

        public BootstrapResult(@NonNull UserProfile profile, boolean isNewUser) {
            this.profile = profile;
            this.isNewUser = isNewUser;
        }

        public UserProfile getProfile() {
            return profile;
        }

        public boolean isNewUser() {
            return isNewUser;
        }
    }

    private final AuthDeviceService authDeviceService;
    private final FirebaseRepository repository;

    public ProfileService() {
        this.authDeviceService = new AuthDeviceService();
        this.repository = new FirebaseRepository();
    }

    public void bootstrapCurrentUser(
            @NonNull Context context,
            @NonNull OnSuccessListener<BootstrapResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            String installationId = InstallationIdProvider.getInstallationId(context);

            repository.isAdminInstallation(installationId, isAdmin -> {
                String desiredRole = isAdmin ? "admin" : "user";

                repository.getUser(uid, existingProfile -> {
                    if (existingProfile == null) {
                        createDefaultProfile(uid, installationId, desiredRole, onSuccess, onFailure);
                        return;
                    }

                    boolean needsUpdate = false;

                    if (!desiredRole.equals(existingProfile.getRole())) {
                        existingProfile.setRole(desiredRole);
                        needsUpdate = true;
                    }

                    if (existingProfile.getInstallationId() == null
                            || !installationId.equals(existingProfile.getInstallationId())) {
                        existingProfile.setInstallationId(installationId);
                        needsUpdate = true;
                    }

                    if (needsUpdate) {
                        repository.updateUser(existingProfile,
                                unused -> onSuccess.onSuccess(new BootstrapResult(existingProfile, false)),
                                onFailure);
                    } else {
                        onSuccess.onSuccess(new BootstrapResult(existingProfile, false));
                    }
                }, onFailure);
            }, onFailure);
        }, onFailure);
    }

    public void ensureSignedIn(
            @NonNull OnSuccessListener<FirebaseUser> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(onSuccess, onFailure);
    }

    public void completeCurrentProfile(
            @NonNull Context context,
            @NonNull String name,
            @NonNull String email,
            String phone,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        bootstrapCurrentUser(context, result -> {
            UserProfile profile = result.getProfile();
            profile.setName(name);
            profile.setEmail(email);
            profile.setPhone(phone);

            repository.updateUser(profile,
                    unused -> onSuccess.onSuccess(profile),
                    onFailure);
        }, onFailure);
    }

    public void loadCurrentProfile(
            @NonNull Context context,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        bootstrapCurrentUser(context, result -> onSuccess.onSuccess(result.getProfile()), onFailure);
    }

    public boolean requiresProfileCompletion(@NonNull UserProfile profile) {
        return !profile.hasRequiredProfileInfo();
    }

    private void createDefaultProfile(
            @NonNull String uid,
            @NonNull String installationId,
            @NonNull String role,
            @NonNull OnSuccessListener<BootstrapResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        UserProfile newProfile = new UserProfile(
                uid,
                null,
                null,
                null,
                role,
                true,
                installationId
        );

        repository.saveUser(newProfile,
                unused -> onSuccess.onSuccess(new BootstrapResult(newProfile, true)),
                onFailure);
    }
    public void deleteCurrentProfile(
            @NonNull Context context,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            repository.deleteUser(uid, onSuccess, onFailure);
        }, onFailure);
    }

    public void setNotificationsMuted(


    @NonNull Context context,
            boolean muted,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {android.util.Log.d("HELIOS_MUTE", "setNotificationsMuted called, muted=" + muted);
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            repository.setNotificationsMuted(uid, muted, onSuccess, onFailure);
        }, onFailure);
    }
}