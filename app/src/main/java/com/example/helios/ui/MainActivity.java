package com.example.helios.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.data.NotificationRepository;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.example.helios.service.HeliosFirebaseMessagingService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.NotificationNavArgs;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main activity of the application.
 * Manages the top-level navigation, bottom navigation bars (entrant and organizer),
 * and user profile display.
 */
public class MainActivity extends ThemedActivity {

    private ProfileService profileService;
    private NotificationRepository notificationRepository;
    private com.example.helios.auth.AuthDeviceService authDeviceService;

    private BottomNavigationView bottomNav;
    private BottomNavigationView bottomNavOrganizer;
    private String organizerEventId;

    private NavHostFragment navHostFragment;
    private NavController navController;

    private TextView bannerText;
    private TextView bannerRole;

    private UserProfile cachedProfile = null;
    private Boolean cachedProfileComplete = null; // null = unknown
    private ListenerRegistration notificationListenerRegistration;
    private final Set<String> observedNotificationIds = new HashSet<>();
    private boolean hasLoadedNotificationSnapshot = false;

    private int lastSelectedItemId = R.id.eventsFragment;
    private @MenuRes int currentBottomMenuRes = R.menu.bottom_nav_menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyHeliosTheme();
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(this);
        profileService = application.getProfileService();
        notificationRepository = application.getNotificationRepository();
        authDeviceService = application.getAuthDeviceService();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        requestNotificationPermissionIfNeeded();

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNavOrganizer = findViewById(R.id.bottom_nav_organizer);

        View banner = findViewById(R.id.include_user_banner);
        bannerText = banner.findViewById(R.id.user_banner_text);
        bannerRole = banner.findViewById(R.id.user_banner_role);

        navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) throw new IllegalStateException("NavHostFragment missing");
        navController = navHostFragment.getNavController();

        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setOnItemSelectedListener(createBottomNavListener());

        bottomNavOrganizer.setOnItemSelectedListener(createOrganizerBottomNavListener());

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();

            if (EventNavArgs.hasEventId(arguments)) {
                organizerEventId = EventNavArgs.getEventId(arguments);
            }

                boolean shouldUseEntrantMenu =
                        destId == R.id.eventsFragment
                                || destId == R.id.scanQrFragment
                                || destId == R.id.organizeFragment
                                || destId == R.id.createEventFragment
                                || destId == R.id.profileFragment
                                || destId == R.id.notificationsFragment
                                || destId == R.id.adminFragment;

            boolean shouldUseOrganizerMenu =
                    destId == R.id.manageEventFragment
                            || destId == R.id.editEventFragment
                            || destId == R.id.viewEventQrFragment
                            || destId == R.id.notifyEntrantsFragment
                            || destId == R.id.organizerViewEntrantsFragment
                            || destId == R.id.privateEventInviteFragment
                            || destId == R.id.assignCoOrganizerFragment
                            || destId == R.id.entrantMapFragment;

            if (shouldUseOrganizerMenu) {
                showOrganizerBottomNav(destId);
            } else if (shouldUseEntrantMenu) {
                showMainBottomNav(destId);
                if (destId == R.id.organizeFragment) {
                    organizerEventId = null;
                }
            }
        });

        applyInsets();
        refreshUserBanner();
        consumeNotificationLaunchIntent(getIntent());

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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeNotificationLaunchIntent(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }

    /**
     * Shows the main entrant bottom navigation bar.
     *
     * @param destinationId The current destination ID to set as checked.
     */
    private void showMainBottomNav(int destinationId) {
        bottomNav.setVisibility(View.VISIBLE);
        bottomNavOrganizer.setVisibility(View.GONE);
        setCheckedTabSilently(destinationId);

        MenuItem adminItem = bottomNav.getMenu().findItem(R.id.adminFragment);
        if (adminItem != null && cachedProfile != null) {
            adminItem.setVisible(cachedProfile.isAdmin());
        }
    }

    /**
     * Shows the organizer-specific bottom navigation bar.
     *
     * @param destinationId The current destination ID to set as checked.
     */
    private void showOrganizerBottomNav(int destinationId) {
        bottomNav.setVisibility(View.GONE);
        bottomNavOrganizer.setVisibility(View.VISIBLE);
        setOrganizerCheckedTabSilently(mapOrganizerTab(destinationId));
    }

    /**
     * Maps complex destination IDs to their corresponding root tab ID in the organizer menu.
     *
     * @param destinationId The destination ID to map.
     * @return The mapped menu item ID.
     */
    private int mapOrganizerTab(int destinationId) {
        if (destinationId == R.id.editEventFragment
                || destinationId == R.id.organizerViewEntrantsFragment
                || destinationId == R.id.privateEventInviteFragment
                || destinationId == R.id.assignCoOrganizerFragment
                || destinationId == R.id.entrantMapFragment) {
            return R.id.manageEventFragment;
        }
        return destinationId;
    }

    /**
     * Sets the checked state of an item in the organizer bottom navigation bar without triggering listeners.
     *
     * @param itemId The menu item ID to check.
     */
    private void setOrganizerCheckedTabSilently(int itemId) {
        MenuItem item = bottomNavOrganizer.getMenu().findItem(itemId);
        if (item != null) {
            item.setChecked(true);
        }
    }

    /**
     * Creates a listener for the organizer bottom navigation bar.
     *
     * @return The navigation bar selection listener.
     */
    private NavigationBarView.OnItemSelectedListener createOrganizerBottomNavListener() {
        return item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.organizeFragment) {
                navController.navigate(R.id.organizeFragment);
                return true;
            }

            if (organizerEventId == null || organizerEventId.trim().isEmpty()) {
                Toast.makeText(this, "Missing event id.", Toast.LENGTH_SHORT).show();
                return false;
            }

            Bundle args = EventNavArgs.forEventId(organizerEventId);

            if (itemId == R.id.manageEventFragment) {
                navController.navigate(R.id.manageEventFragment, args);
                return true;
            }

            if (itemId == R.id.viewEventQrFragment) {
                navController.navigate(R.id.viewEventQrFragment, args);
                return true;
            }

            if (itemId == R.id.notifyEntrantsFragment) {
                navController.navigate(R.id.notifyEntrantsFragment, args);
                return true;
            }

            return false;
        };
    }

    /**
     * Navigates to a top-level destination within the navigation graph.
     *
     * @param destinationId The ID of the destination fragment.
     * @return True if navigation was successful, false otherwise.
     */
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

    /**
     * Checks if the user's profile is complete before allowing navigation to the Organize section.
     * If incomplete, prompts the user to setup their profile.
     */
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
            if (cachedProfile != null && cachedProfile.isOrganizerAccessRevoked()) {
                Toast.makeText(this,
                        "Organizer access has been restricted on this profile.",
                        Toast.LENGTH_LONG).show();
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
            cachedProfileComplete = profile != null && !profileService.requiresProfileCompletion(profile);

            if (profile == null || !cachedProfileComplete) {
                openProfileSetupPrompt();
                setCheckedTabSilently(lastSelectedItemId);
                return;
            }
            if (profile.isOrganizerAccessRevoked()) {
                Toast.makeText(this,
                        "Organizer access has been restricted on this profile.",
                        Toast.LENGTH_LONG).show();
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

    /**
     * Starts the ProfileSetupActivity when profile information is required.
     */
    private void openProfileSetupPrompt() {
        Toast.makeText(this,
                "Name and email are required to organize events.",
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_MODE, ProfileSetupActivity.MODE_SETUP_REQUIRED);
        intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
        startActivity(intent);
    }

    /**
     * Sets the checked state of an item in the main bottom navigation bar without triggering listeners.
     *
     * @param itemId The menu item ID to check.
     */
    private void setCheckedTabSilently(int itemId) {
        MenuItem item = bottomNav.getMenu().findItem(itemId);
        if (item != null) item.setChecked(true);
    }

    /**
     * Applies window insets to handle system bars (status bar, navigation bar) padding.
     */
    private void applyInsets() {
        View root = findViewById(R.id.root);
        View topInsetContainer = findViewById(R.id.top_inset_container);
        int topInsetPaddingLeft = topInsetContainer.getPaddingLeft();
        int topInsetPaddingRight = topInsetContainer.getPaddingRight();
        int topInsetPaddingBottom = topInsetContainer.getPaddingBottom();
        int bottomNavPaddingLeft = bottomNav.getPaddingLeft();
        int bottomNavPaddingTop = bottomNav.getPaddingTop();
        int bottomNavPaddingRight = bottomNav.getPaddingRight();
        int bottomNavPaddingBottom = bottomNav.getPaddingBottom();
        int organizerBottomNavPaddingLeft = bottomNavOrganizer.getPaddingLeft();
        int organizerBottomNavPaddingTop = bottomNavOrganizer.getPaddingTop();
        int organizerBottomNavPaddingRight = bottomNavOrganizer.getPaddingRight();
        int organizerBottomNavPaddingBottom = bottomNavOrganizer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            int extraTop = getResources().getDimensionPixelSize(R.dimen.helios_top_safe_gap);

            topInsetContainer.setPadding(
                    topInsetPaddingLeft,
                    topInset + extraTop,
                    topInsetPaddingRight,
                    topInsetPaddingBottom
            );

            bottomNav.setPadding(
                    bottomNavPaddingLeft,
                    bottomNavPaddingTop,
                    bottomNavPaddingRight,
                    bottomNavPaddingBottom + bottomInset
            );

            bottomNavOrganizer.setPadding(
                    organizerBottomNavPaddingLeft,
                    organizerBottomNavPaddingTop,
                    organizerBottomNavPaddingRight,
                    organizerBottomNavPaddingBottom + bottomInset
            );

            return insets;
        });
    }

    /**
     * Refreshes the user information displayed in the top banner and manages admin menu visibility.
     */
    private void refreshUserBanner() {
        profileService.loadCurrentProfile(this, profile -> {
            cachedProfile = profile;
            cachedProfileComplete = profile != null && !profileService.requiresProfileCompletion(profile);

            if (profile == null) {
                bannerText.setText("Signed in as: profile setup required");
                bannerRole.setText("");
                MenuItem adminItem = bottomNav.getMenu().findItem(R.id.adminFragment);
                if (adminItem != null) {
                    adminItem.setVisible(false);
                }
                return;
            }

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
        startNotificationListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopNotificationListener();
    }

    /**
     * Creates the BottomNavigationView listener for the entrant bottom navigation menu.
     * Handles specific gating logic for the Organize tab and Admin access.
     *
     * @return The navigation bar selection listener.
     */
    private BottomNavigationView.OnItemSelectedListener createBottomNavListener() {
        return item -> {
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
        };
    }

    private void startNotificationListener() {
        if (notificationListenerRegistration != null) {
            return;
        }

        String uid = authDeviceService.getCurrentUid();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        observedNotificationIds.clear();
        hasLoadedNotificationSnapshot = false;
        notificationListenerRegistration = notificationRepository.subscribeNotificationsForUser(
                uid,
                this::handleLiveNotifications,
                error -> {}
        );
    }

    private void stopNotificationListener() {
        if (notificationListenerRegistration != null) {
            notificationListenerRegistration.remove();
            notificationListenerRegistration = null;
        }
        observedNotificationIds.clear();
        hasLoadedNotificationSnapshot = false;
    }

    private void handleLiveNotifications(@NonNull List<NotificationRecord> records) {
        if (!hasLoadedNotificationSnapshot) {
            for (NotificationRecord record : records) {
                if (record != null && record.getNotificationId() != null) {
                    observedNotificationIds.add(record.getNotificationId());
                }
            }
            hasLoadedNotificationSnapshot = true;
            return;
        }

        for (int i = records.size() - 1; i >= 0; i--) {
            NotificationRecord record = records.get(i);
            if (record == null || record.getNotificationId() == null) {
                continue;
            }
            if (!observedNotificationIds.add(record.getNotificationId())) {
                continue;
            }

            HeliosFirebaseMessagingService.showLocalNotification(
                    this,
                    record.getTitle(),
                    record.getMessage(),
                    record.getEventId(),
                    record.getNotificationId()
            );
        }
    }

    private void consumeNotificationLaunchIntent(@Nullable Intent intent) {
        if (!NotificationNavArgs.isNotificationIntent(intent)) {
            return;
        }

        openNotificationTarget(NotificationNavArgs.getEventId(intent));
        NotificationNavArgs.clear(intent);
        setIntent(intent);
    }

    public void openNotificationTarget(@Nullable String eventId) {
        if (!HeliosText.isNonEmpty(eventId)) {
            lastSelectedItemId = R.id.notificationsFragment;
            navigateTopLevel(R.id.notificationsFragment);
            return;
        }

        navHostFragment.getChildFragmentManager().setFragmentResult(
                NotificationNavArgs.REQUEST_OPEN_EVENT,
                EventNavArgs.forEventId(eventId)
        );
        lastSelectedItemId = R.id.eventsFragment;
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() != R.id.eventsFragment) {
            navigateTopLevel(R.id.eventsFragment);
        }
    }
}
