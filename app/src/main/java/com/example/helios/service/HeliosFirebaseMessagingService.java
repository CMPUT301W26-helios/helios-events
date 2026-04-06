package com.example.helios.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.ui.MainActivity;
import com.example.helios.ui.common.NotificationNavArgs;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class HeliosFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "helios_events";
    private static final String TAG = "HeliosFcmService";

    @Override
    public void onNewToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        HeliosApplication application = HeliosApplication.from(this);
        ProfileService profileService = application.getProfileService();
        profileService.loadCurrentProfile(
                getApplicationContext(),
                profile -> {
                    if (profile != null && profile.getUid() != null) {
                        application.getRepository()
                                .saveFcmToken(
                                        profile.getUid(),
                                        token,
                                        unused -> {},
                                        error -> Log.w(TAG, "Failed to persist refreshed FCM token.", error)
                                );
                    }
                },
                error -> Log.w(TAG, "Failed to load profile for refreshed FCM token.", error)
        );
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = "Helios Events";
        String body = "";
        String eventId = remoteMessage.getData().get("eventId");
        String notificationId = remoteMessage.getData().get("notificationId");

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (remoteMessage.getData().containsKey("title")) {
            title = remoteMessage.getData().get("title");
        }
        if (body.isEmpty() && remoteMessage.getData().containsKey("body")) {
            body = remoteMessage.getData().get("body");
        }

        showLocalNotification(this, title, body, eventId, notificationId);
    }

    public static void showLocalNotification(
            @NonNull Context context,
            @Nullable String title,
            @Nullable String body,
            @Nullable String eventId,
            @Nullable String notificationId
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        NotificationNavArgs.markNotificationIntent(intent, eventId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId != null ? notificationId.hashCode() : 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null && !title.trim().isEmpty() ? title : "Helios Events")
                .setContentText(body != null ? body : "")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body != null ? body : ""))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        int localId = notificationId != null
                ? notificationId.hashCode()
                : (int) System.currentTimeMillis();
        NotificationManagerCompat.from(context).notify(localId, builder.build());
    }

    private static void createNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Event Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
