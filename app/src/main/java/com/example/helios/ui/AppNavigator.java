package com.example.helios.ui;

import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

import com.example.helios.R;
import com.example.helios.service.ProfileService;

public class AppNavigator {

    private final AppCompatActivity activity;
    private final NavController navController;
    private final ProfileService profileService;

    private int lastSelectedItemId = R.id.eventsFragment;

    public AppNavigator(
            @NonNull AppCompatActivity activity,
            @NonNull NavController navController
    ) {
        this.activity = activity;
        this.navController = navController;
        this.profileService = new ProfileService();
    }

    public int getLastSelectedItemId() {
        return lastSelectedItemId;
    }

    public void setLastSelectedItemId(int id) {
        lastSelectedItemId = id;
    }

    /** Tabs */
    public void goEvents() {
        lastSelectedItemId = R.id.eventsFragment;
        safeNavigate(R.id.eventsFragment);
    }

    public void goScanQr() {
        lastSelectedItemId = R.id.scanQrFragment;
        safeNavigate(R.id.scanQrFragment);
    }

    public void goProfile() {
        lastSelectedItemId = R.id.profileFragment;
        safeNavigate(R.id.profileFragment);
    }

    public void goNotifications() {
        lastSelectedItemId = R.id.notificationsFragment;
        safeNavigate(R.id.notificationsFragment);
    }

    /**
     * Organizer is a "complex action" tab:
     * - if profile incomplete -> open ProfileSetupActivity prompt
     * - else -> navigate to Organize tab
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
    private void safeNavigate(int destinationId) {
        // Avoid crashing if already at destination
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return;
        }
        navController.navigate(destinationId);
    }
}