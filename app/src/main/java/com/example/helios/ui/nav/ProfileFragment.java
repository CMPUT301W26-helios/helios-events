package com.example.helios.ui.nav;

import android.content.res.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.UserProfile;
import com.example.helios.service.AccessibilityPreferences;
import com.example.helios.service.HeliosUiPreferences;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.ThemedActivity;
import com.example.helios.ui.LauncherActivity;
import com.example.helios.ui.ProfileSetupActivity;
import com.example.helios.ui.common.HeliosChipFactory;
import com.example.helios.ui.common.HeliosImageUploader;
import com.example.helios.ui.theme.HeliosFontOption;
import com.example.helios.ui.theme.HeliosThemeManager;
import com.example.helios.ui.theme.HeliosThemeOption;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.UUID;

/**
 * Fragment that displays the user's profile information.
 * Allows users to edit their profile, delete their account, toggle notification settings,
 * and access the admin panel if they have administrative privileges.
 */
public class ProfileFragment extends Fragment {
    private static final String NOTIFICATIONS_ON_EMOJI = "\uD83D\uDD14";
    private static final String NOTIFICATIONS_OFF_EMOJI = "\uD83D\uDD15";

    private ProfileService profileService;

    private TextView tvName;
    private TextView tvEmail;
    private TextView tvPhone;
    private ImageView ivProfilePicture;
    private MaterialButton btnAdmin;
    private MaterialButton btnChangePhoto;

    private MaterialButton btnEdit;
    private MaterialButton btnDelete;
    private MaterialButton btnMute;
    private ProgressBar profilePhotoProgress;
    private TextView tvNotificationSummary;
    private TextView tvAccessibilitySummary;
    private ChipGroup cgThemeOptions;
    private ChipGroup cgFontOptions;
    private SwitchMaterial switchColorBlind;
    private SwitchMaterial switchLargeText;
    private SwitchMaterial switchLargeTouchTargets;
    private SwitchMaterial switchHeaderHeliosIcon;
    private SwitchMaterial switchSignInBanner;
    private AccessibilityPreferences accessibilityPreferences;
    private HeliosUiPreferences uiPreferences;
    private HeliosThemeManager themeManager;
    private TextView tvHeaderIconSummary;

    private boolean notificationsCurrentlyEnabled = true;
    @Nullable private String profileImageUrl;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickProfileImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) {
                    return;
                }
                persistReadPermission(uri);
                uploadSelectedProfilePhoto(uri);
            });

    /**
     * Default constructor for ProfileFragment.
     */
    public ProfileFragment() {
        super(R.layout.fragment_profile_screen);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvHeaderTitle = view.findViewById(R.id.tvScreenTitle);
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText("Profile");
        }

        profileService = HeliosApplication.from(requireContext()).getProfileService();
        tvName = view.findViewById(R.id.tv_name);
        tvEmail = view.findViewById(R.id.tv_email);
        tvPhone = view.findViewById(R.id.tv_phone);
        ivProfilePicture = view.findViewById(R.id.iv_profile_picture);
        btnAdmin = view.findViewById(R.id.btn_admin);
        btnChangePhoto = view.findViewById(R.id.btn_change_photo);
        profilePhotoProgress = view.findViewById(R.id.progress_profile_photo);
        if (btnAdmin != null) {
            btnAdmin.setVisibility(View.GONE);
        }

        btnEdit = view.findViewById(R.id.btn_edit);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnMute = view.findViewById(R.id.btn_mute_notification);
        tvNotificationSummary = view.findViewById(R.id.tv_notification_summary);
        tvAccessibilitySummary = view.findViewById(R.id.tv_accessibility_summary);
        cgThemeOptions = view.findViewById(R.id.cg_theme_options);
        cgFontOptions = view.findViewById(R.id.cg_font_options);
        switchColorBlind = view.findViewById(R.id.switchColorBlind);
        switchLargeText = view.findViewById(R.id.switchLargeText);
        switchLargeTouchTargets = view.findViewById(R.id.switchLargeTouchTargets);
        switchHeaderHeliosIcon = view.findViewById(R.id.switchHeaderHeliosIcon);
        switchSignInBanner = view.findViewById(R.id.switchSignInBanner);
        tvHeaderIconSummary = view.findViewById(R.id.tv_header_icon_summary);
        accessibilityPreferences = HeliosApplication.from(requireContext()).getAccessibilityPreferences();
        uiPreferences = HeliosApplication.from(requireContext()).getUiPreferences();
        themeManager = HeliosApplication.from(requireContext()).getThemeManager();
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> openProfileEditorSafe());
        }
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        }
        if (btnMute != null) {
            btnMute.setOnClickListener(v -> toggleMute());
        }
        if (btnAdmin != null) {
            btnAdmin.setOnClickListener(v -> openAdminPanel());
        }
        if (ivProfilePicture != null) {
            ivProfilePicture.setOnClickListener(v -> launchProfileImagePicker());
        }
        if (btnChangePhoto != null) {
            btnChangePhoto.setOnClickListener(v -> launchProfileImagePicker());
        }
        bindAccessibilityPreferences();
        bindHeaderIconPreference();
        bindThemeOptions();
        bindFontOptions();


        loadProfileSafe();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindAccessibilityPreferences();
        bindHeaderIconPreference();
        bindThemeOptions();
        bindFontOptions();
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
        if (btnMute == null) {
            return;
        }
        boolean notificationsOn = notificationsCurrentlyEnabled;
        int backgroundColor = MaterialColors.getColor(
                btnMute,
                notificationsOn
                        ? com.google.android.material.R.attr.colorTertiaryContainer
                        : com.google.android.material.R.attr.colorErrorContainer
        );
        int textColor = MaterialColors.getColor(
                btnMute,
                notificationsOn
                        ? com.google.android.material.R.attr.colorOnTertiaryContainer
                        : com.google.android.material.R.attr.colorOnErrorContainer
        );
        int strokeColor = textColor;

        btnMute.setText(notificationsOn
                ? "[" + NOTIFICATIONS_ON_EMOJI + "] Notifications on"
                : "[" + NOTIFICATIONS_OFF_EMOJI + "] Notifications off");
        btnMute.setContentDescription(notificationsOn
                ? "Notifications are on. Double tap to turn notifications off."
                : "Notifications are off. Double tap to turn notifications on.");
        btnMute.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        btnMute.setTextColor(textColor);
        btnMute.setStrokeColor(ColorStateList.valueOf(strokeColor));
        if (tvNotificationSummary != null) {
            tvNotificationSummary.setText(notificationsOn
                    ? "Push alerts are on for lottery updates, invitations, and organizer messages."
                    : "Push alerts are paused on this account until you turn them back on.");
        }
    }

    private void bindAccessibilityPreferences() {
        if (accessibilityPreferences == null
                || switchColorBlind == null
                || switchLargeText == null
                || switchLargeTouchTargets == null) {
            return;
        }

        switchColorBlind.setOnCheckedChangeListener(null);
        switchLargeText.setOnCheckedChangeListener(null);
        switchLargeTouchTargets.setOnCheckedChangeListener(null);
        switchColorBlind.setChecked(accessibilityPreferences.isColorBlindMode());
        switchLargeText.setChecked(accessibilityPreferences.isLargeTextMode());
        switchLargeTouchTargets.setChecked(accessibilityPreferences.isLargeTouchTargetsMode());

        switchColorBlind.setOnCheckedChangeListener((buttonView, isChecked) -> {
            accessibilityPreferences.setColorBlindMode(isChecked);
            updateAccessibilitySummary();
            showAccessibilityRestartSnackbar();
        });

        switchLargeText.setOnCheckedChangeListener((buttonView, isChecked) -> {
            accessibilityPreferences.setLargeTextMode(isChecked);
            updateAccessibilitySummary();
            showAccessibilityRestartSnackbar();
        });

        switchLargeTouchTargets.setOnCheckedChangeListener((buttonView, isChecked) -> {
            accessibilityPreferences.setLargeTouchTargetsMode(isChecked);
            updateAccessibilitySummary();
            showAccessibilityRestartSnackbar();
        });

        updateAccessibilitySummary();
    }

    private void bindHeaderIconPreference() {
        if (switchHeaderHeliosIcon == null || uiPreferences == null) {
            return;
        }

        switchHeaderHeliosIcon.setOnCheckedChangeListener(null);
        switchHeaderHeliosIcon.setChecked(uiPreferences.isHeaderIconEnabled());
        switchHeaderHeliosIcon.setOnCheckedChangeListener((buttonView, isChecked) -> {
            uiPreferences.setHeaderIconEnabled(isChecked);
            updateHeaderIconSummary(isChecked);
            if (requireActivity() instanceof ThemedActivity) {
                ((ThemedActivity) requireActivity()).refreshHeaderBranding();
            }
        });
        updateHeaderIconSummary(switchHeaderHeliosIcon.isChecked());
    }

    private void updateHeaderIconSummary(boolean enabled) {
        if (tvHeaderIconSummary == null) {
            return;
        }
        tvHeaderIconSummary.setText(enabled
                ? "The Helios icon is shown beside screen titles across the app."
                : "Screen titles are shown without the Helios icon across the app.");
    }

    private void showAccessibilityRestartSnackbar() {
        View view = getView();
        if (view == null) return;

        Snackbar.make(view, "Refresh the screen to apply accessibility changes.", Snackbar.LENGTH_LONG)
                .setAction("Refresh", actionView -> requireActivity().recreate())
                .show();
    }

    private void bindThemeOptions() {
        if (cgThemeOptions == null || themeManager == null) {
            return;
        }

        cgThemeOptions.removeAllViews();
        HeliosThemeOption selectedTheme = themeManager.getThemeOption();
        for (HeliosThemeOption option : HeliosThemeOption.values()) {
            Chip chip = HeliosChipFactory.createCheckableChip(
                    requireContext(),
                    option.getDisplayName(),
                    option == selectedTheme
            );
            chip.setContentDescription(option.getDisplayName() + ". " + option.getDescription());
            chip.setOnClickListener(v -> applyThemeSelection(option));
            cgThemeOptions.addView(chip);
        }
    }

    private void applyThemeSelection(@NonNull HeliosThemeOption option) {
        if (themeManager == null) {
            return;
        }
        if (themeManager.getThemeOption() == option) {
            bindThemeOptions();
            return;
        }

        themeManager.setThemeOption(option);
        bindThemeOptions();
        requireActivity().recreate();
    }

    private void updateAccessibilitySummary() {
        if (tvAccessibilitySummary == null || accessibilityPreferences == null) {
            return;
        }

        StringBuilder builder = new StringBuilder("Active: ");
        boolean hasMode = false;

        if (accessibilityPreferences.isColorBlindMode()) {
            builder.append("colorblind-friendly palette");
            hasMode = true;
        }
        if (accessibilityPreferences.isLargeTextMode()) {
            if (hasMode) {
                builder.append(", ");
            }
            builder.append("larger text");
            hasMode = true;
        }
        if (accessibilityPreferences.isLargeTouchTargetsMode()) {
            if (hasMode) {
                builder.append(", ");
            }
            builder.append("larger tap targets");
            hasMode = true;
        }

        if (!hasMode) {
            tvAccessibilitySummary.setText("Active: standard sizing and your selected theme palette.");
            return;
        }

        tvAccessibilitySummary.setText(builder.append('.').toString());
    }

    private void bindFontOptions() {
        if (cgFontOptions == null || themeManager == null) {
            return;
        }

        cgFontOptions.removeAllViews();
        HeliosFontOption selectedFont = themeManager.getFontOption();
        for (HeliosFontOption option : HeliosFontOption.values()) {
            Chip chip = HeliosChipFactory.createCheckableChip(
                    requireContext(),
                    option.getDisplayName(),
                    option == selectedFont
            );
            chip.setContentDescription(option.getDisplayName() + ". " + option.getDescription());
            chip.setOnClickListener(v -> applyFontSelection(option));
            cgFontOptions.addView(chip);
        }
    }

    private void applyFontSelection(@NonNull HeliosFontOption option) {
        if (themeManager == null) {
            return;
        }
        if (themeManager.getFontOption() == option) {
            bindFontOptions();
            return;
        }

        themeManager.setFontOption(option);
        bindFontOptions();
        requireActivity().recreate();
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
            if (btnAdmin != null) {
                btnAdmin.setVisibility(
                        "admin".equals(profile.getRole()) ? View.VISIBLE : View.GONE
                );
            }

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
        if (tvName != null) {
            tvName.setText("Profile not set up yet");
        }
        if (tvEmail != null) {
            tvEmail.setText("Add a name and email to finish your account.");
        }
        if (tvPhone != null) {
            tvPhone.setVisibility(View.GONE);
        }
        if (btnAdmin != null) {
            btnAdmin.setVisibility(View.GONE);
        }
        profileImageUrl = null;
        showProfileImagePlaceholder();
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
        profileImageUrl = normalize(profile.getProfileImageUrl());

        if (tvName != null) {
            tvName.setText(name != null ? name : "Name not set");
        }
        if (tvEmail != null) {
            tvEmail.setText(email != null ? email : "Email not set");
        }

        if (tvPhone != null) {
            if (phone != null) {
                tvPhone.setVisibility(View.VISIBLE);
                tvPhone.setText(phone);
            } else {
                tvPhone.setVisibility(View.GONE);
            }
        }
        bindProfileImage(profileImageUrl);
        bindSignInBannerPreference(profile);
    }

    private void bindSignInBannerPreference(@NonNull UserProfile profile) {
        if (switchSignInBanner == null) return;

        switchSignInBanner.setOnCheckedChangeListener(null);
        switchSignInBanner.setChecked(profile.isSignInBannerEnabled());
        switchSignInBanner.setOnCheckedChangeListener((buttonView, isChecked) -> {
            profileService.setSignInBannerEnabled(requireContext(), isChecked,
                    unused -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(),
                                isChecked ? "Banner enabled." : "Banner disabled.",
                                Toast.LENGTH_SHORT).show();
                    },
                    error -> {
                        if (!isAdded()) return;
                        switchSignInBanner.setChecked(!isChecked); // Revert UI
                        Toast.makeText(getContext(),
                                "Failed to update banner preference: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });
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

    private void launchProfileImagePicker() {
        pickProfileImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void uploadSelectedProfilePhoto(@NonNull Uri photoUri) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        setPhotoUploadInProgress(true);
        profileService.ensureSignedIn(firebaseUser ->
                        HeliosImageUploader.uploadImage(
                                ctx,
                                photoUri,
                                "profile_pictures/" + firebaseUser.getUid() + "/" + UUID.randomUUID(),
                                uploadResult -> profileService.updateCurrentProfilePhoto(
                                        ctx,
                                        uploadResult.getDownloadUrl(),
                                        profile -> {
                                            if (!isAdded()) return;
                                            setPhotoUploadInProgress(false);
                                            bindProfile(profile);
                                            Toast.makeText(
                                                    requireContext(),
                                                    "Profile photo updated.",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        },
                                        error -> {
                                            if (!isAdded()) return;
                                            setPhotoUploadInProgress(false);
                                            Toast.makeText(
                                                    requireContext(),
                                                    "Photo update failed: " + error.getMessage(),
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        }
                                ),
                                error -> {
                                    if (!isAdded()) return;
                                    setPhotoUploadInProgress(false);
                                    Toast.makeText(
                                            requireContext(),
                                            "Photo upload failed: "
                                                    + HeliosImageUploader.getUserFacingUploadErrorMessage(error),
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                        ),
                error -> {
                    if (!isAdded()) return;
                    setPhotoUploadInProgress(false);
                    Toast.makeText(
                            requireContext(),
                            "Auth failed: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    private void bindProfileImage(@Nullable String imageUrl) {
        if (ivProfilePicture == null) {
            return;
        }
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            showProfileImagePlaceholder();
            return;
        }

        Glide.with(this).clear(ivProfilePicture);
        ivProfilePicture.setImageTintList(null);
        ivProfilePicture.setColorFilter(null);
        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_avatar_person_placeholder)
                .error(R.drawable.ic_avatar_person_placeholder)
                .circleCrop()
                .into(ivProfilePicture);
    }

    private void showProfileImagePlaceholder() {
        if (ivProfilePicture == null) {
            return;
        }
        Glide.with(this).clear(ivProfilePicture);
        ivProfilePicture.setImageTintList(null);
        ivProfilePicture.setColorFilter(null);
        ivProfilePicture.setImageResource(R.drawable.ic_avatar_person_placeholder);
    }

    private void setPhotoUploadInProgress(boolean inProgress) {
        if (profilePhotoProgress != null) {
            profilePhotoProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        }
        if (btnChangePhoto != null) {
            btnChangePhoto.setEnabled(!inProgress);
        }
        if (ivProfilePicture != null) {
            ivProfilePicture.setEnabled(!inProgress);
        }
    }

    private void persistReadPermission(@NonNull Uri uri) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        try {
            ctx.getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }
}
