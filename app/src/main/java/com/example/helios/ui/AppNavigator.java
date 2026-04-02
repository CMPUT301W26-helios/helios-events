package com.example.helios.ui;

import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

import com.example.helios.R;
import com.example.helios.service.ProfileService;

/**
 * Helper class for handling navigation between top-level fragments and activities.
 * Manages tab selection and profile-based access control for certain sections.
 */
public class AppNavigator {

    private final AppCompatActivity activity;
    private final NavController navController;
    private final ProfileService profileService;

    private int lastSelectedItemId = R.id.eventsFragment;

    /**
     * Constructs an AppNavigator.
     *
     * @param activity      The host activity.
     * @param navController The navigation controller managing fragment transitions.
     */
    public AppNavigator(
            @NonNull AppCompatActivity activity,
            @NonNull NavController navController
    ) {
        this.activity = activity;
        this.navController = navController;
        this.profileService = new ProfileService();
    }

    /**
     * @return The ID of the last selected navigation item.
     */
    public int getLastSelectedItemId() {
        return lastSelectedItemId;
    }

    /**
     * @param id The ID of the navigation item to set as last selected.
     */
    public void setLastSelectedItemId(int id) {
        lastSelectedItemId = id;
    }

    /**
     * Navigates to the Events fragment.
     */
    public void goEvents() {
        lastSelectedItemId = R.id.eventsFragment;
        safeNavigate(R.id.eventsFragment);
    }

    /**
     * Navigates to the QR Scan fragment.
     */
    public void goScanQr() {
        lastSelectedItemId = R.id.scanQrFragment;
        safeNavigate(R.id.scanQrFragment);
    }

    /**
     * Navigates to the Profile fragment.
     */
    public void goProfile() {
        lastSelectedItemId = R.id.profileFragment;
        safeNavigate(R.id.profileFragment);
    }

    /**
     * Navigates to the Notifications fragment.
     */
    public void goNotifications() {
        lastSelectedItemId = R.id.notificationsFragment;
        safeNavigate(R.id.notificationsFragment);
    }

    /**
     * Navigates to the Organize fragment if the profile is complete.
     * If the profile is incomplete, prompts the user to complete it via {@link ProfileSetupActivity}.
     */
    public void goOrganizeOrPrompt() {
        profileService.loadCurrentProfile(activity, profile -> {
            if (profileService.requiresProfileCompletion(profile)) {
                Toast.makeText(activity,
                        "Name and email are required to organize events.",
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent = new Intent(activity, ProfileSetupActivity.class);
                intent.putExtra(ProfileSetupActivity.EXTRA_MODE, ProfileSetupActivity.MODE_SETUP_REQUIRED);
                intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
                activity.startActivity(intent);

                // do NOT switch nav tab
                return;
            }

            lastSelectedItemId = R.id.organizeFragment;
            safeNavigate(R.id.organizeFragment);
        }, error -> Toast.makeText(activity,
                "Profile check failed: " + error.getMessage(),
                Toast.LENGTH_LONG
        ).show());
    }

    /**
     * Safely navigates to a destination if it's not already the current destination.
     *
     * @param destinationId The ID of the destination to navigate to.
     */
    private void safeNavigate(int destinationId) {
        // Avoid crashing if already at destination
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return;
        }
        navController.navigate(destinationId);
    }
}
