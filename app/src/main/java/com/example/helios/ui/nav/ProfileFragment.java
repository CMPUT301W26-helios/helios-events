package com.example.helios.ui.nav;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.R;
import com.example.helios.model.UserProfile;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.ProfileSetupActivity;
import com.google.android.material.button.MaterialButton;
import com.example.helios.ui.LauncherActivity;

/**
 * Fragment that displays the user's profile information.
 * Allows users to edit their profile, delete their account, toggle notification settings,
 * and access the admin panel if they have administrative privileges.
 */
public class ProfileFragment extends Fragment {

    private final ProfileService profileService = new ProfileService();

    private TextView tvName;
    private TextView tvEmail;
    private TextView tvPhone;
    private MaterialButton btnAdmin;

    private MaterialButton btnEdit;
    private MaterialButton btnDelete;
    private MaterialButton btnMute;

    private boolean notificationsCurrentlyEnabled = true;

    /**
     * Default constructor for ProfileFragment.
     */
    public ProfileFragment() {
        super(R.layout.fragment_profile_screen);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvName = view.findViewById(R.id.tv_name);
        tvEmail = view.findViewById(R.id.tv_email);
        tvPhone = view.findViewById(R.id.tv_phone);
        btnAdmin = view.findViewById(R.id.btn_admin);
        btnAdmin.setVisibility(View.GONE); // hide by default

        btnEdit = view.findViewById(R.id.btn_edit);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnMute = view.findViewById(R.id.btn_mute_notification);
        btnEdit.setOnClickListener(v -> openProfileEditorSafe());
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        btnMute.setOnClickListener(v -> toggleMute());
        btnAdmin.setOnClickListener(v -> openAdminPanel());


        loadProfileSafe();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileSafe();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Shows a confirmation dialog before deleting the user's profile.
     */
    private void showDeleteConfirmationDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        new AlertDialog.Builder(ctx)
                .setTitle("Delete Profile")
                .setMessage("Are you sure you want to delete your profile? This cannot be undone.")
                .setPositiveButton("I'm sure", (dialog, which) -> deleteProfile())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Deletes the user's profile and returns them to the launcher activity.
     */
    private void deleteProfile() {
        Context ctx = getContext();
        if (ctx == null) return;

        profileService.deleteCurrentProfile(ctx,
                unused -> {
                    if (!isAdded() || getContext() == null) return;
                    Intent intent = new Intent(getContext(), LauncherActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                },
                error -> {
                    if (!isAdded() || getContext() == null) return;
                    Toast.makeText(getContext(),
                            "Delete failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ── Mute / Unmute ─────────────────────────────────────────────────────────

    /**
     * Toggles the notification mute status for the current user.
     */
    private void toggleMute() {
        Context ctx = getContext();

        if (ctx == null) return;

        boolean nowMuting = notificationsCurrentlyEnabled; // if enabled → we are muting
        profileService.setNotificationsMuted(ctx, nowMuting,
                unused -> {
                    if (!isAdded() || getContext() == null) return;
                    notificationsCurrentlyEnabled = !nowMuting;
                    updateMuteButton();
                    String msg = nowMuting ? "Notifications muted." : "Notifications unmuted.";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (!isAdded() || getContext() == null) return;
                    Toast.makeText(getContext(),
                            "Failed to update notifications: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Updates the text of the mute button based on the current notification status.
     */
    private void updateMuteButton() {
        // 🔇 when enabled (can mute), 🔊 when muted (can unmute)
        btnMute.setText(notificationsCurrentlyEnabled ? "🔇" : "🔊");
    }

    /**
     * Navigates to the admin panel fragment.
     */
    private void openAdminPanel() {
        NavController navController = NavHostFragment.findNavController(this);
        navController.navigate(R.id.adminFragment);
    }

    // ── Profile loading ───────────────────────────────────────────────────────

    /**
     * Navigates to the profile editor activity.
     */
    private void openProfileEditorSafe() {
        Context ctx = getContext();
        if (ctx == null) return;

        Intent intent = new Intent(ctx, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_MODE, ProfileSetupActivity.MODE_EDIT_PROFILE);
        intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
        startActivity(intent);
    }

    /**
     * Loads the user's profile information from {@link ProfileService} and updates the UI.
     */
    private void loadProfileSafe() {
        Context ctx = getContext();
        if (ctx == null || getView() == null) return;

        profileService.loadCurrentProfile(ctx, profile -> {
            if (!isAdded() || getView() == null) return;

            if (profile == null) {
                bindEmptyProfile();
                return;
            }

            notificationsCurrentlyEnabled = profile.isNotificationsEnabled();
            updateMuteButton();
            bindProfile(profile);
            btnAdmin.setVisibility(
                    "admin".equals((profile.getRole())) ? View.VISIBLE : View.GONE
            );

        }, error -> {
            if (!isAdded() || getContext() == null) return;
            Toast.makeText(getContext(),
                    "Failed to load profile: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Displays default placeholder text when no profile information is available.
     */
    private void bindEmptyProfile() {
        tvName.setText("Name: (not set)");
        tvEmail.setText("Email: (not set)");
        tvPhone.setVisibility(View.GONE);
    }

    /**
     * Binds the provided user profile data to the UI views.
     *
     * @param profile The user profile to display.
     */
    private void bindProfile(@NonNull UserProfile profile) {
        String name  = normalize(profile.getName());
        String email = normalize(profile.getEmail());
        String phone = normalize(profile.getPhone());

        tvName.setText("Name: "  + (name  != null ? name  : "(not set)"));
        tvEmail.setText("Email: " + (email != null ? email : "(not set)"));

        if (phone != null) {
            tvPhone.setVisibility(View.VISIBLE);
            tvPhone.setText("Phone (Optional): " + phone);
        } else {
            tvPhone.setVisibility(View.GONE);
        }
    }

    /**
     * Trims a string and returns null if the resulting string is empty.
     *
     * @param s The string to normalize.
     * @return The trimmed string or null if empty.
     */
    @Nullable
    private String normalize(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
