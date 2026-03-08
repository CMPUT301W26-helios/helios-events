package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.helios.R;
import com.example.helios.model.UserProfile;
import com.example.helios.service.ProfileService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final ProfileService profileService = new ProfileService();

    private BottomNavigationView bottomNav;
    private NavController navController;

    private TextView bannerText;
    private TextView bannerRole;

    private UserProfile cachedProfile = null;
    private Boolean cachedProfileComplete = null; // null = unknown

    private int lastSelectedItemId = R.id.eventsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);

        View banner = findViewById(R.id.include_user_banner);
        bannerText = banner.findViewById(R.id.user_banner_text);
        bannerRole = banner.findViewById(R.id.user_banner_role);

        NavHostFragment navHost =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) throw new IllegalStateException("NavHostFragment missing");
        navController = navHost.getNavController();

        // Keep bottom nav in sync when back button / programmatic nav happens
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Intercept selection so we can gate Organize and avoid tab stacking
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.organizeFragment) {
                gateOrganizeAndNavigate();
                return false;
            }

            // Admin tab should only be reachable if visible, but guard anyway
            if (id == R.id.adminFragment) {
                if (cachedProfile != null && cachedProfile.isAdmin()) {
                    boolean handled = navigateTopLevel(id);
                    if (handled) lastSelectedItemId = id;
                    return handled;
                } else {
                    Toast.makeText(this, "Admin access required.", Toast.LENGTH_SHORT).show();
                    setCheckedTabSilently(lastSelectedItemId);
                    return false;
                }
            }

            boolean handled = navigateTopLevel(id);
            if (handled) lastSelectedItemId = id;
            return handled;
        });

        applyInsets();
        refreshUserBanner();

        // Back should pop within nav graph; if nothing left, finish
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navController.navigateUp()) {
                    finish();
                }
            }
        });
    }

    private boolean navigateTopLevel(int destinationId) {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            return true;
        }

        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();

        try {
            navController.navigate(destinationId, null, options);
            return true;
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Navigation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void gateOrganizeAndNavigate() {
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.organizeFragment) {
            setCheckedTabSilently(R.id.organizeFragment);
            lastSelectedItemId = R.id.organizeFragment;
            return;
        }

        if (cachedProfileComplete != null) {
            if (!cachedProfileComplete) {
                openProfileSetupPrompt();
                setCheckedTabSilently(lastSelectedItemId);
                return;
            }
            lastSelectedItemId = R.id.organizeFragment;
            navigateTopLevel(R.id.organizeFragment);
            setCheckedTabSilently(R.id.organizeFragment);
            return;
        }

        Toast.makeText(this, "Loading profile...", Toast.LENGTH_SHORT).show();
        profileService.loadCurrentProfile(this, profile -> {
            cachedProfile = profile;
            cachedProfileComplete = !profileService.requiresProfileCompletion(profile);

            if (!cachedProfileComplete) {
                openProfileSetupPrompt();
                setCheckedTabSilently(lastSelectedItemId);
                return;
            }

            lastSelectedItemId = R.id.organizeFragment;
            navigateTopLevel(R.id.organizeFragment);
            setCheckedTabSilently(R.id.organizeFragment);
        }, error -> Toast.makeText(this,
                "Profile check failed: " + error.getMessage(),
                Toast.LENGTH_LONG).show());
    }

    private void openProfileSetupPrompt() {
        Toast.makeText(this,
                "Name and email are required to organize events.",
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_MODE, ProfileSetupActivity.MODE_SETUP_REQUIRED);
        intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
        startActivity(intent);
    }

    private void setCheckedTabSilently(int itemId) {
        MenuItem item = bottomNav.getMenu().findItem(itemId);
        if (item != null) item.setChecked(true);
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        View topInsetContainer = findViewById(R.id.top_inset_container);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            int extraTop = getResources().getDimensionPixelSize(R.dimen.helios_top_safe_gap);

            topInsetContainer.setPadding(
                    topInsetContainer.getPaddingLeft(),
                    topInset + extraTop,
                    topInsetContainer.getPaddingRight(),
                    topInsetContainer.getPaddingBottom()
            );

            bottomNav.setPadding(
                    bottomNav.getPaddingLeft(),
                    bottomNav.getPaddingTop(),
                    bottomNav.getPaddingRight(),
                    bottomInset
            );

            return insets;
        });
    }

    private void refreshUserBanner() {
        profileService.loadCurrentProfile(this, profile -> {
            cachedProfile = profile;
            cachedProfileComplete = !profileService.requiresProfileCompletion(profile);

            String name = profile.getDisplayNameOrFallback();
            if (name == null) name = "Anonymous";
            bannerText.setText("Signed in as: " + name);
            bannerRole.setText(profile.isAdmin() ? "admin" : "user");

            // Toggle Admin menu visibility
            MenuItem adminItem = bottomNav.getMenu().findItem(R.id.adminFragment);
            if (adminItem != null) {
                adminItem.setVisible(profile.isAdmin());
            }

            // If admin got hidden while user is on Admin, bounce out
            if (!profile.isAdmin()
                    && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.adminFragment) {
                lastSelectedItemId = R.id.eventsFragment;
                navigateTopLevel(R.id.eventsFragment);
                setCheckedTabSilently(R.id.eventsFragment);
            }

        }, error -> {
            cachedProfile = null;
            cachedProfileComplete = null;
            bannerText.setText("Signed in as: (error)");
            bannerRole.setText("");

            // Hide admin tab on error
            MenuItem adminItem = bottomNav.getMenu().findItem(R.id.adminFragment);
            if (adminItem != null) adminItem.setVisible(false);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUserBanner();
    }
}