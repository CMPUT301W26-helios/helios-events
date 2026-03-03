package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.model.UserProfile;
import com.example.helios.service.ProfileService;

public class MainActivity extends AppCompatActivity {
    private AppNavigator appNavigator;
    private boolean isAdmin;

    private final ProfileService profileService = new ProfileService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isAdmin = getIntent().getBooleanExtra("is_admin", false);

        appNavigator = new AppNavigator(this, R.id.main_fragment_container);

        if (savedInstanceState == null) {
            appNavigator.openEntrantHome();

            if (isAdmin) {
                Toast.makeText(this, "Admin mode detected", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void requireProfileForComplexAction(Runnable onAllowed) {
        profileService.loadCurrentProfile(this, profile -> {
            if (profileService.requiresProfileCompletion(profile)) {
                Intent intent = new Intent(this, ProfileSetupActivity.class);
                intent.putExtra(ProfileSetupActivity.EXTRA_FIRST_TIME, false);
                intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
                startActivity(intent);
                return;
            }

            onAllowed.run();
        }, error -> Toast.makeText(this, "Profile check failed: " + error.getMessage(), Toast.LENGTH_LONG).show());
    }
}