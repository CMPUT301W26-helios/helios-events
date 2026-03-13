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

import java.util.List;

/**
 * Service class that provides business logic for managing user profiles.
 * It handles profile bootstrapping, completion, and retrieval.
 */
public class ProfileService {

    /**
     * Result object for the profile bootstrapping process.
     */

    @FunctionalInterface
    interface InstallationIdSource {
        String getInstallationId(@NonNull Context context);
    }

    public static class BootstrapResult {
        private final UserProfile profile;
        private final boolean isNewUser;

        /**
         * Constructs a new BootstrapResult.
         *
         * @param profile   The user profile.
         * @param isNewUser True if the profile was just created, false if it existed.
         */
        public BootstrapResult(@NonNull UserProfile profile, boolean isNewUser) {
            this.profile = profile;
            this.isNewUser = isNewUser;
        }

        /**
         * @return The user profile.
         */
        public UserProfile getProfile() {
            return profile;
        }

        /**
         * @return True if the profile was just created, false if it existed.
         */
        public boolean isNewUser() {
            return isNewUser;
        }
    }

    private final AuthDeviceService authDeviceService;
    private final FirebaseRepository repository;
    private final InstallationIdSource installationIdSource;

    /**
     * Initializes the ProfileService with default dependencies.
     */
    public ProfileService() {
        this(
                new AuthDeviceService(),
                new FirebaseRepository(),
                InstallationIdProvider::getInstallationId
        );
    }

    // Package-private test seam
    ProfileService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull FirebaseRepository repository,
            @NonNull InstallationIdSource installationIdSource
    ) {
        this.authDeviceService = authDeviceService;
        this.repository = repository;
        this.installationIdSource = installationIdSource;
    }

    /**
     * Bootstraps the current user's profile. This includes ensuring the user is signed in,
     * checking for an existing profile, and creating a default one if necessary.
     * It also syncs the user's role based on their device's admin status.
     *
     * @param context   The application context.
     * @param onSuccess Callback receiving the BootstrapResult.
     * @param onFailure Callback for failed operation.
     */
    public void bootstrapCurrentUser(
            @NonNull Context context,
            @NonNull OnSuccessListener<BootstrapResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            String installationId = installationIdSource.getInstallationId(context);

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
                        repository.updateUser(
                                existingProfile,
                                unused -> onSuccess.onSuccess(new BootstrapResult(existingProfile, false)),
                                onFailure
                        );
                    } else {
                        onSuccess.onSuccess(new BootstrapResult(existingProfile, false));
                    }
                }, onFailure);
            }, onFailure);
        }, onFailure);
    }

    /**
     * Ensures the user is signed in.
     *
     * @param onSuccess Callback receiving the FirebaseUser.
     * @param onFailure Callback for failed authentication.
     */
    public void ensureSignedIn(
            @NonNull OnSuccessListener<FirebaseUser> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(onSuccess, onFailure);
    }

    /**
     * Completes the current user's profile with personal details.
     *
     * @param context   The application context.
     * @param name      The user's name.
     * @param email     The user's email address.
     * @param phone     The user's phone number.
     * @param onSuccess Callback receiving the updated UserProfile.
     * @param onFailure Callback for failed operation.
     */
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

            repository.updateUser(
                    profile,
                    unused -> onSuccess.onSuccess(profile),
                    onFailure
            );
        }, onFailure);
    }

    /**
     * Loads the current user's profile.
     *
     * @param context   The application context.
     * @param onSuccess Callback receiving the UserProfile.
     * @param onFailure Callback for failed operation.
     */
    public void loadCurrentProfile(
            @NonNull Context context,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        bootstrapCurrentUser(context, result -> onSuccess.onSuccess(result.getProfile()), onFailure);
    }

    /**
     * Retrieves a user profile by UID.
     *
     * @param uid       The unique identifier of the user.
     * @param onSuccess Callback receiving the UserProfile.
     * @param onFailure Callback for failed operation.
     */
    public void getUserProfile(
            @NonNull String uid,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getUser(uid, onSuccess, onFailure);
    }

    /**
     * Checks if the profile requires completion (i.e., missing name or email).
     *
     * @param profile The user profile to check.
     * @return True if information is missing, false otherwise.
     */
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

        repository.saveUser(
                newProfile,
                unused -> onSuccess.onSuccess(new BootstrapResult(newProfile, true)),
                onFailure
        );
    }

    /**
     * Deletes the profile of the current user.
     *
     * @param context   The application context.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
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

    /**
     * Retrieves all user profiles.
     *
     * @param onSuccess Callback receiving the list of user profiles.
     * @param onFailure Callback for failed operation.
     */
    public void getAllProfiles(
            @NonNull OnSuccessListener<List<UserProfile>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllUsers(onSuccess, onFailure);
    }

    /**
     * Deletes a user profile by UID.
     *
     * @param uid       The unique identifier of the user to delete.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void deleteProfile(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.deleteUser(uid, onSuccess, onFailure);
    }

    /**
     * Updates the notification mute status for the current user.
     *
     * @param context   The application context.
     * @param muted     True to mute notifications, false to enable.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void setNotificationsMuted(
            @NonNull Context context,
            boolean muted,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            repository.setNotificationsMuted(uid, muted, onSuccess, onFailure);
        }, onFailure);
    }
}