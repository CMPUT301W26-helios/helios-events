package com.example.helios;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.data.NotificationRepository;
import com.example.helios.service.AccessibilityPreferences;
import com.example.helios.service.AdminGeolocationTestService;
import com.example.helios.service.AdminNotificationTestService;
import com.example.helios.service.CommentService;
import com.example.helios.service.EntrantEventService;
import com.example.helios.service.EventService;
import com.example.helios.service.ImageService;
import com.example.helios.service.LotteryService;
import com.example.helios.service.OrganizerNotificationService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.HeliosUiPreferences;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.theme.HeliosThemeManager;
import com.google.firebase.messaging.FirebaseMessaging;

public class HeliosApplication extends Application {
    private static final String TAG = "HeliosApplication";

    private FirebaseRepository repository;
    private AuthDeviceService authDeviceService;
    private ProfileService profileService;
    private EventService eventService;
    private EntrantEventService entrantEventService;
    private WaitingListService waitingListService;
    private OrganizerNotificationService organizerNotificationService;
    private LotteryService lotteryService;
    private CommentService commentService;
    private ImageService imageService;
    private AdminGeolocationTestService adminGeolocationTestService;
    private AdminNotificationTestService adminNotificationTestService;
    private AccessibilityPreferences accessibilityPreferences;
    private HeliosUiPreferences uiPreferences;
    private HeliosThemeManager themeManager;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new FirebaseRepository();
        authDeviceService = new AuthDeviceService();
        profileService = new ProfileService(authDeviceService, repository, repository);
        eventService = new EventService(repository);
        organizerNotificationService = new OrganizerNotificationService(repository, repository, repository);
        entrantEventService = new EntrantEventService(repository, repository, profileService);
        waitingListService = new WaitingListService(repository);
        lotteryService = new LotteryService(repository, repository, organizerNotificationService);
        commentService = new CommentService(repository, profileService, eventService);
        imageService = new ImageService(this, repository);
        adminGeolocationTestService = new AdminGeolocationTestService(
                authDeviceService,
                repository,
                repository,
                repository
        );
        adminNotificationTestService = new AdminNotificationTestService(
                authDeviceService,
                repository,
                repository,
                repository,
                organizerNotificationService
        );
        accessibilityPreferences = new AccessibilityPreferences(this);
        uiPreferences = new HeliosUiPreferences(this);
        themeManager = new HeliosThemeManager(this);
        initializeFcmTokenSync();
    }

    public static HeliosApplication from(@NonNull Context context) {
        return (HeliosApplication) context.getApplicationContext();
    }

    public FirebaseRepository getRepository() {
        return repository;
    }

    public AuthDeviceService getAuthDeviceService() {
        return authDeviceService;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public EntrantEventService getEntrantEventService() {
        return entrantEventService;
    }

    public WaitingListService getWaitingListService() {
        return waitingListService;
    }

    public OrganizerNotificationService getOrganizerNotificationService() {
        return organizerNotificationService;
    }

    public LotteryService getLotteryService() {
        return lotteryService;
    }

    public CommentService getCommentService() {
        return commentService;
    }

    public ImageService getImageService() {
        return imageService;
    }

    public NotificationRepository getNotificationRepository() {
        return repository;
    }

    public AdminNotificationTestService getAdminNotificationTestService() {
        return adminNotificationTestService;
    }

    public AdminGeolocationTestService getAdminGeolocationTestService() {
        return adminGeolocationTestService;
    }

    public AccessibilityPreferences getAccessibilityPreferences() {
        return accessibilityPreferences;
    }

    public HeliosUiPreferences getUiPreferences() {
        return uiPreferences;
    }

    public HeliosThemeManager getThemeManager() {
        return themeManager;
    }

    private void initializeFcmTokenSync() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty()) {
                        return;
                    }
                    profileService.loadCurrentProfile(
                            getApplicationContext(),
                            profile -> {
                                if (profile == null || profile.getUid() == null) {
                                    return;
                                }
                                repository.saveFcmToken(
                                        profile.getUid(),
                                        token,
                                        unused -> {},
                                        error -> Log.w(TAG, "Failed to persist FCM token during app init.", error)
                                );
                            },
                            error -> Log.w(TAG, "Failed to load profile for FCM token sync.", error)
                    );
                });
    }
}
