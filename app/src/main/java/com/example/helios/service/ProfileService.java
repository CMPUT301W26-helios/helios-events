package com.example.helios.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.auth.InstallationIdProvider;
import com.example.helios.data.EventRepository;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.UserRepository;
import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/**
 * Service responsible for user profile bootstrap, completion, role synchronization,
 * and notification/sign-in preferences for the current device user.
 */
public class ProfileService {

    /**
     * Result object for the profile bootstrapping process.
     */
    @FunctionalInterface
    public interface InstallationIdSource {
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

        /** @return The user profile. */
        public UserProfile getProfile() { return profile; }

        /** @return True if the profile was just created, false if it existed. */
        public boolean isNewUser() { return isNewUser; }
    }

    private final AuthDeviceService authDeviceService;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final InstallationIdSource installationIdSource;

    public ProfileService() {
        this(new FirebaseRepository());
    }

    public ProfileService(@NonNull FirebaseRepository repository) {
        this(
                new AuthDeviceService(),
                repository,
                repository,
                InstallationIdProvider::getInstallationId
        );
    }

    public ProfileService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull UserRepository userRepository,
            @NonNull EventRepository eventRepository
    ) {
        this(authDeviceService, userRepository, eventRepository, InstallationIdProvider::getInstallationId);
    }

    // Visible for testing
    public ProfileService(
            @NonNull AuthDeviceService authDeviceService,
            @NonNull UserRepository userRepository,
            @NonNull EventRepository eventRepository,
            @NonNull InstallationIdSource installationIdSource
    ) {
        this.authDeviceService = authDeviceService;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
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

            userRepository.isAdminInstallation(installationId, isAdmin -> {
                String desiredRole = isAdmin ? "admin" : "user";

                userRepository.getUser(uid, existingProfile -> {
                    if (existingProfile == null) {
                        createDefaultProfile(uid, installationId, desiredRole, onSuccess, onFailure);
                        return;
                    }

                    boolean needsUpdate = repairBootstrapProfile(
                            existingProfile,
                            uid,
                            installationId,
                            desiredRole
                    );

                    if (needsUpdate) {
                        userRepository.updateUser(
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

            userRepository.updateUser(
                    profile,
                    unused -> onSuccess.onSuccess(profile),
                    onFailure
            );
        }, onFailure);
    }

    /**
     * Completes the current user's profile and replaces the stored profile photo URL.
     *
     * @param context         The application context.
     * @param name            The user's name.
     * @param email           The user's email address.
     * @param phone           The user's phone number.
     * @param profileImageUrl The uploaded profile image URL.
     * @param onSuccess       Callback receiving the updated UserProfile.
     * @param onFailure       Callback for failed operation.
     */
    public void completeCurrentProfileWithPhoto(
            @NonNull Context context,
            @NonNull String name,
            @NonNull String email,
            String phone,
            String profileImageUrl,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        bootstrapCurrentUser(context, result -> {
            UserProfile profile = result.getProfile();
            profile.setName(name);
            profile.setEmail(email);
            profile.setPhone(phone);
            profile.setProfileImageUrl(profileImageUrl);

            userRepository.updateUser(
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
     * Updates only the current user's stored profile image URL.
     *
     * @param context         The application context.
     * @param profileImageUrl The uploaded profile image URL.
     * @param onSuccess       Callback receiving the updated UserProfile.
     * @param onFailure       Callback for failed operation.
     */
    public void updateCurrentProfilePhoto(
            @NonNull Context context,
            String profileImageUrl,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        // A photo update only needs the current UID — skip the admin_devices round-trip
        // that bootstrapCurrentUser always performs. Two Firestore calls instead of three.
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            userRepository.getUser(uid, profile -> {
                if (profile == null) {
                    onFailure.onFailure(new IllegalStateException("User profile not found."));
                    return;
                }
                profile.setProfileImageUrl(profileImageUrl);
                userRepository.updateUser(
                        profile,
                        unused -> onSuccess.onSuccess(profile),
                        onFailure
                );
            }, onFailure);
        }, onFailure);
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
        userRepository.getUser(uid, onSuccess, onFailure);
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
        newProfile.setSignInBannerEnabled(true);

        userRepository.saveUser(
                newProfile,
                unused -> onSuccess.onSuccess(new BootstrapResult(newProfile, true)),
                onFailure
        );
    }

    private boolean repairBootstrapProfile(
            @NonNull UserProfile profile,
            @NonNull String uid,
            @NonNull String installationId,
            @NonNull String desiredRole
    ) {
        boolean needsUpdate = false;

        if (!uid.equals(profile.getUid())) {
            profile.setUid(uid);
            needsUpdate = true;
        }

        if (!desiredRole.equals(profile.getRole())) {
            profile.setRole(desiredRole);
            needsUpdate = true;
        }

        if (!installationId.equals(profile.getInstallationId())) {
            profile.setInstallationId(installationId);
            needsUpdate = true;
        }

        return needsUpdate;
    }

    /**
     * Deletes the profile of the current user, along with all events they organized.
     * Events are deleted first; the user document is only removed once all event
     * deletions complete successfully.
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
            deleteUserAndEvents(uid, onSuccess, onFailure);
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
        userRepository.getAllUsers(onSuccess, onFailure);
    }

    /**
     * Deletes a user profile by UID, along with all events they organized.
     * Events are deleted first; the user document is only removed once all event
     * deletions complete successfully.
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
        deleteUserAndEvents(uid, onSuccess, onFailure);
    }

    /**
     * Internal helper that deletes all events organized by the given UID first,
     * then deletes the user document itself.
     *
     * @param uid       The UID of the user to fully remove.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    private void deleteUserAndEvents(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        eventRepository.deleteEventsByOrganizer(
                uid,
                unused -> userRepository.deleteUser(uid, onSuccess, onFailure),
                onFailure
        );
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
            userRepository.setNotificationsMuted(uid, muted, onSuccess, onFailure);
        }, onFailure);
    }

    /**
     * Updates whether the sign-in banner is enabled for the current user.
     *
     * @param context   The application context.
     * @param enabled   True to show the banner, false to hide it.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void setSignInBannerEnabled(
            @NonNull Context context,
            boolean enabled,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        authDeviceService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            userRepository.setSignInBannerEnabled(uid, enabled, onSuccess, onFailure);
        }, onFailure);
    }

    /**
     * Updates whether organizer access is blocked for the given user profile.
     *
     * @param uid       The unique identifier of the user to update.
     * @param revoked   True to block organizer access, false to restore it.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void setOrganizerAccessRevoked(
            @NonNull String uid,
            boolean revoked,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        userRepository.getUser(uid, profile -> {
            if (profile == null) {
                onFailure.onFailure(new IllegalArgumentException("User profile not found."));
                return;
            }
            profile.setOrganizerAccessRevoked(revoked);
            userRepository.updateUser(profile, onSuccess, onFailure);
        }, onFailure);
    }
}
