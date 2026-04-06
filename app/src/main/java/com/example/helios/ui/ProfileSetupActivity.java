package com.example.helios.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.common.HeliosImageUploader;
import com.google.android.material.button.MaterialButton;

import java.util.UUID;

/**
 * Activity for setting up or editing a user profile.
 * Requires name and email to be provided for account completion.
 */
public class ProfileSetupActivity extends ThemedActivity {

    /** Intent extra key to indicate if the activity should return to MainActivity after completion. */
    public static final String EXTRA_RETURN_TO_MAIN = "extra_return_to_main";

    /** Intent extra key to specify the operation mode (setup or edit). */
    public static final String EXTRA_MODE = "extra_mode";
    /** Mode for initial profile setup when required information is missing. */
    public static final String MODE_SETUP_REQUIRED = "setup_required";
    /** Mode for editing an existing profile. */
    public static final String MODE_EDIT_PROFILE = "edit_profile";

    private ProfileService profileService;

    private EditText nameInput;
    private EditText emailInput;
    private EditText phoneInput;
    private ImageView profileImageView;
    private MaterialButton profilePhotoButton;
    private ProgressBar profilePhotoProgress;

    private MaterialButton primaryButton;    // "Sign up" / "Save"

    private TextView titleText;
    private TextView subtitleText;
    private View contentContainer;

    private boolean returnToMain;
    private String mode;
    @Nullable private Uri pendingProfileImageUri;
    @Nullable private String currentProfileImageUrl;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickProfileImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) {
                    return;
                }
                persistReadPermission(uri);
                pendingProfileImageUri = uri;
                showSelectedProfileImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyHeliosTheme();
        super.onCreate(savedInstanceState);
        profileService = HeliosApplication.from(this).getProfileService();
        setContentView(R.layout.activity_profile_setup);

        returnToMain = getIntent().getBooleanExtra(EXTRA_RETURN_TO_MAIN, false);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_SETUP_REQUIRED;

        titleText = findViewById(R.id.submenu_title);
        subtitleText = findViewById(R.id.submenu_subtitle);
        contentContainer = findViewById(R.id.profile_setup_content);

        nameInput = findViewById(R.id.edit_name);
        emailInput = findViewById(R.id.edit_email);
        phoneInput = findViewById(R.id.edit_phone);
        profileImageView = findViewById(R.id.iv_profile_setup_picture);
        profilePhotoButton = findViewById(R.id.button_profile_photo);
        profilePhotoProgress = findViewById(R.id.progress_profile_setup_photo);

        primaryButton = findViewById(R.id.button_sign_up);

        configureUiForMode();
        findViewById(R.id.submenu_back_button).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        applyInsets();
        attachValidationWatchers();
        showProfilePlaceholder();
        profileImageView.setOnClickListener(v -> openProfileImagePicker());
        profilePhotoButton.setOnClickListener(v -> openProfileImagePicker());

        if (MODE_EDIT_PROFILE.equals(mode)) {
            prefillCurrentProfile();
        }

        primaryButton.setOnClickListener(v -> saveProfile());
    }

    /**
     * Configures the UI elements (titles, button text) based on the current mode.
     */
    private void configureUiForMode() {
        if (MODE_EDIT_PROFILE.equals(mode)) {
            titleText.setText("Edit profile");
            subtitleText.setText("Update the details attached to your Helios account.");
            primaryButton.setText("Save changes");
        } else {
            titleText.setText("Finish your profile");
            subtitleText.setText("Name and email are required before you can join events or manage them.");
            primaryButton.setText("Save and continue");
        }

        primaryButton.setEnabled(false);
    }

    private void applyInsets() {
        int paddingLeft = contentContainer.getPaddingLeft();
        int paddingTop = contentContainer.getPaddingTop();
        int paddingRight = contentContainer.getPaddingRight();
        int paddingBottom = contentContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (view, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int extraTop = getResources().getDimensionPixelSize(R.dimen.helios_top_safe_gap);
            view.setPadding(
                    paddingLeft,
                    paddingTop + topInset + extraTop,
                    paddingRight,
                    paddingBottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(contentContainer);
    }

    /**
     * Attaches text watchers to input fields to validate required fields in real-time.
     */
    private void attachValidationWatchers() {
        TextWatcher watcher = new SimpleWatcher(this::updatePrimaryButtonState);
        nameInput.addTextChangedListener(watcher);
        emailInput.addTextChangedListener(watcher);
    }

    /**
     * Loads the current user's profile and prefills the input fields.
     */
    private void prefillCurrentProfile() {
        profileService.loadCurrentProfile(this, profile -> {
            if (profile == null) return;

            if (profile.getName() != null) nameInput.setText(profile.getName());
            if (profile.getEmail() != null) emailInput.setText(profile.getEmail());
            if (profile.getPhone() != null) phoneInput.setText(profile.getPhone());
            currentProfileImageUrl = profile.getProfileImageUrl();
            bindRemoteProfileImage(currentProfileImageUrl);

            updatePrimaryButtonState();
        }, error -> Toast.makeText(this,
                "Failed to load profile: " + error.getMessage(),
                Toast.LENGTH_LONG).show());
    }

    /**
     * Updates the enabled state of the primary action button based on input validity.
     */
    private void updatePrimaryButtonState() {
        primaryButton.setEnabled(hasRequiredInputs());
    }

    /**
     * Checks if the required input fields (name and email) are not empty.
     * @return True if valid, false otherwise.
     */
    private boolean hasRequiredInputs() {
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
        return !name.isEmpty() && !email.isEmpty();
    }

    /**
     * Saves the profile information using {@link ProfileService}.
     */
    private void saveProfile() {
        String name = nameInput.getText() != null ? nameInput.getText().toString() : "";
        String email = emailInput.getText() != null ? emailInput.getText().toString() : "";
        String phone = phoneInput.getText() != null ? phoneInput.getText().toString() : "";

        if (pendingProfileImageUri != null) {
            uploadPhotoAndSaveProfile(name, email, phone, pendingProfileImageUri);
            return;
        }

        profileService.completeCurrentProfile(
                this,
                name,
                email,
                phone,
                profile -> {
                    Toast.makeText(this, "Profile saved.", Toast.LENGTH_SHORT).show();
                    finishOrGoMain();
                },
                error -> Toast.makeText(this, "Save failed: " + error.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    /**
     * Finishes the activity or navigates to {@link MainActivity} depending on {@link #returnToMain}.
     */
    private void finishOrGoMain() {
        if (!returnToMain) {
            finish();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * A simple implementation of {@link TextWatcher} that runs a callback on text change.
     */
    private static class SimpleWatcher implements TextWatcher {
        private final Runnable callback;
        SimpleWatcher(Runnable callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { callback.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }

    private void openProfileImagePicker() {
        pickProfileImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void uploadPhotoAndSaveProfile(
            @NonNull String name,
            @NonNull String email,
            @NonNull String phone,
            @NonNull Uri photoUri
    ) {
        setPhotoUploadInProgress(true);
        profileService.ensureSignedIn(firebaseUser ->
                        HeliosImageUploader.uploadImage(
                                this,
                                photoUri,
                                "profile_pictures/" + firebaseUser.getUid() + "/" + UUID.randomUUID(),
                                uploadResult -> {
                                    currentProfileImageUrl = uploadResult.getDownloadUrl();
                                    profileService.completeCurrentProfileWithPhoto(
                                            this,
                                            name,
                                            email,
                                            phone,
                                            currentProfileImageUrl,
                                            profile -> {
                                                setPhotoUploadInProgress(false);
                                                Toast.makeText(this, "Profile saved.", Toast.LENGTH_SHORT).show();
                                                finishOrGoMain();
                                            },
                                            error -> {
                                                setPhotoUploadInProgress(false);
                                                Toast.makeText(
                                                        this,
                                                        "Save failed: " + error.getMessage(),
                                                        Toast.LENGTH_LONG
                                                ).show();
                                            }
                                    );
                                },
                                error -> {
                                    setPhotoUploadInProgress(false);
                                    Toast.makeText(
                                            this,
                                            "Photo upload failed: "
                                                    + HeliosImageUploader.getUserFacingUploadErrorMessage(error),
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                        ),
                error -> {
                    setPhotoUploadInProgress(false);
                    Toast.makeText(this, "Auth failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }

    private void bindRemoteProfileImage(@Nullable String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            showProfilePlaceholder();
            return;
        }
        Glide.with(this).clear(profileImageView);
        profileImageView.setImageTintList(null);
        profileImageView.setColorFilter(null);
        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_avatar_person_placeholder)
                .error(R.drawable.ic_avatar_person_placeholder)
                .circleCrop()
                .into(profileImageView);
    }

    private void showSelectedProfileImage(@NonNull Uri imageUri) {
        Glide.with(this).clear(profileImageView);
        profileImageView.setImageTintList(null);
        profileImageView.setColorFilter(null);
        Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_avatar_person_placeholder)
                .error(R.drawable.ic_avatar_person_placeholder)
                .circleCrop()
                .into(profileImageView);
    }

    private void showProfilePlaceholder() {
        Glide.with(this).clear(profileImageView);
        profileImageView.setImageTintList(null);
        profileImageView.setColorFilter(null);
        profileImageView.setImageResource(R.drawable.ic_avatar_person_placeholder);
    }

    private void setPhotoUploadInProgress(boolean inProgress) {
        primaryButton.setEnabled(!inProgress && hasRequiredInputs());
        profilePhotoButton.setEnabled(!inProgress);
        profileImageView.setEnabled(!inProgress);
        profilePhotoProgress.setVisibility(inProgress ? ProgressBar.VISIBLE : ProgressBar.GONE);
    }

    private void persistReadPermission(@NonNull Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }
}
