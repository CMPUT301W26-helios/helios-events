package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.service.ProfileService;

public class ProfileSetupActivity extends AppCompatActivity {

    public static final String EXTRA_RETURN_TO_MAIN = "extra_return_to_main";

    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_SETUP_REQUIRED = "setup_required";
    public static final String MODE_EDIT_PROFILE = "edit_profile";

    private final ProfileService profileService = new ProfileService();

    private EditText nameInput;
    private EditText emailInput;
    private EditText phoneInput;

    private Button primaryButton;    // "Sign up" / "Save"
    private Button secondaryButton;  // "Skip" (hidden in edit mode)

    private TextView titleText;
    private TextView subtitleText;

    private boolean returnToMain;
    private String mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        returnToMain = getIntent().getBooleanExtra(EXTRA_RETURN_TO_MAIN, false);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_SETUP_REQUIRED;

        titleText = findViewById(R.id.profile_setup_title);
        subtitleText = findViewById(R.id.profile_setup_subtitle);

        nameInput = findViewById(R.id.edit_name);
        emailInput = findViewById(R.id.edit_email);
        phoneInput = findViewById(R.id.edit_phone);

        primaryButton = findViewById(R.id.button_sign_up);
        secondaryButton = findViewById(R.id.button_skip);

        configureUiForMode();
        attachValidationWatchers();

        if (MODE_EDIT_PROFILE.equals(mode)) {
            prefillCurrentProfile();
        }

        primaryButton.setOnClickListener(v -> saveProfile());
        secondaryButton.setOnClickListener(v -> onSecondaryAction());
    }

    private void configureUiForMode() {
        if (MODE_EDIT_PROFILE.equals(mode)) {
            titleText.setText("Edit Profile");
            subtitleText.setText("Update your info below.");
            primaryButton.setText("Save");
            secondaryButton.setVisibility(View.GONE);
        } else {
            titleText.setText("Profile Required");
            subtitleText.setText("Name and email are required before signing up for events or organizing them.");
            primaryButton.setText("Save  >");
            secondaryButton.setVisibility(View.VISIBLE);
            secondaryButton.setText("Skip profile setup (required later)");
        }

        primaryButton.setEnabled(false);
    }

    private void attachValidationWatchers() {
        TextWatcher watcher = new SimpleWatcher(this::updatePrimaryButtonState);
        nameInput.addTextChangedListener(watcher);
        emailInput.addTextChangedListener(watcher);
    }

    private void prefillCurrentProfile() {
        profileService.loadCurrentProfile(this, profile -> {
            if (profile == null) return;

            if (profile.getName() != null) nameInput.setText(profile.getName());
            if (profile.getEmail() != null) emailInput.setText(profile.getEmail());
            if (profile.getPhone() != null) phoneInput.setText(profile.getPhone());

            updatePrimaryButtonState();
        }, error -> Toast.makeText(this,
                "Failed to load profile: " + error.getMessage(),
                Toast.LENGTH_LONG).show());
    }

    private void updatePrimaryButtonState() {
        primaryButton.setEnabled(hasRequiredInputs());
    }

    private boolean hasRequiredInputs() {
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
        return !name.isEmpty() && !email.isEmpty();
    }

    private void saveProfile() {
        String name = nameInput.getText() != null ? nameInput.getText().toString() : "";
        String email = emailInput.getText() != null ? emailInput.getText().toString() : "";
        String phone = phoneInput.getText() != null ? phoneInput.getText().toString() : "";

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

    private void onSecondaryAction() {
        Toast.makeText(this,
                "You can continue browsing, but a profile will be required for sign-up and organizer actions.",
                Toast.LENGTH_LONG).show();
        finishOrGoMain();
    }

    private void finishOrGoMain() {
        if (!returnToMain) {
            finish();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable callback;
        SimpleWatcher(Runnable callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { callback.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }
}