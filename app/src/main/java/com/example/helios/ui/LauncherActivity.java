package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.model.UserProfile;
import com.example.helios.service.ProfileService;

public class LauncherActivity extends AppCompatActivity {
    private static final long MIN_LOADING_DELAY_MS = 900;

    private final ProfileService profileService = new ProfileService();
    private TextView welcomeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        welcomeText = findViewById(R.id.launcher_welcome_text);

        profileService.bootstrapCurrentUser(this, result -> {
            UserProfile profile = result.getProfile();

            if (result.isNewUser()) {
                welcomeText.setText("Welcome New User!");
            } else {
                String name = profile.getDisplayNameOrFallback();
                if (name != null) {
                    welcomeText.setText("Welcome back " + name + "!");
                } else {
                    welcomeText.setText("Welcome back!");
                }
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (result.isNewUser()) {
                    openProfileSetupRequired();
                } else {
                    openMain(profile);
                }
            }, MIN_LOADING_DELAY_MS);

        }, error -> {
            Toast.makeText(
                    this,
                    "Startup failed: " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
            finish();
        });
    }

    private void openProfileSetupRequired() {
        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_MODE, ProfileSetupActivity.MODE_SETUP_REQUIRED);
        intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, true);
        startActivity(intent);
        finish();
    }

    private void openMain(UserProfile profile) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("is_admin", profile.isAdmin());
        startActivity(intent);
        finish();
    }
}