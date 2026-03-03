package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.service.ProfileService;

public class ProfileSetupActivity extends AppCompatActivity {
    public static final String EXTRA_FIRST_TIME = "extra_first_time";
    public static final String EXTRA_RETURN_TO_MAIN = "extra_return_to_main";

    private final ProfileService profileService = new ProfileService();

    private EditText nameInput;
    private EditText emailInput;
    private EditText phoneInput;
    private Button signUpButton;
    private Button skipButton;
    private TextView titleText;
    private TextView subtitleText;

    private boolean returnToMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        boolean isFirstTime = getIntent().getBooleanExtra(EXTRA_FIRST_TIME, false);
        returnToMain = getIntent().getBooleanExtra(EXTRA_RETURN_TO_MAIN, false);

        titleText = findViewById(R.id.profile_setup_title);
        subtitleText = findViewById(R.id.profile_setup_subtitle);
        nameInput = findViewById(R.id.edit_name);
        emailInput = findViewById(R.id.edit_email);
        phoneInput = findViewById(R.id.edit_phone);
        signUpButton = findViewById(R.id.button_sign_up);
        skipButton = findViewById(R.id.button_skip);

        if (isFirstTime) {
            titleText.setText("Welcome New User!");
            subtitleText.setText("Please Register Below:");
        } else {
            titleText.setText("Profile Required");
            subtitleText.setText("Name and email are required before signing up for events or organizing them.");
        }

        signUpButton.setEnabled(false);

        TextWatcher watcher = new SimpleWatcher(this::updateSignUpButtonState);
        nameInput.addTextChangedListener(watcher);
        emailInput.addTextChangedListener(watcher);

        signUpButton.setOnClickListener(v -> saveProfile());
        skipButton.setOnClickListener(v -> continueWithoutProfile());
    }

    private void updateSignUpButtonState() {
        boolean valid = hasRequiredInputs();
        signUpButton.setEnabled(valid);
    }

    private boolean hasRequiredInputs() {
        return !nameInput.getText().toString().trim().isEmpty()
                && !emailInput.getText().toString().trim().isEmpty();
    }

    private void saveProfile() {
        String name = nameInput.getText().toString();
        String email = emailInput.getText().toString();
        String phone = phoneInput.getText().toString();

        profileService.completeCurrentProfile(
                this,
                name,
                email,
                phone,
                profile -> {
                    Toast.makeText(this, "Profile saved.", Toast.LENGTH_SHORT).show();
                    goToMain();
                },
                error -> Toast.makeText(this, "Save failed: " + error.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void continueWithoutProfile() {
        Toast.makeText(
                this,
                "You can continue browsing, but a profile will be required for sign-up and organizer actions.",
                Toast.LENGTH_LONG
        ).show();

        goToMain();
    }

    private void goToMain() {
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

        SimpleWatcher(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.run();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}