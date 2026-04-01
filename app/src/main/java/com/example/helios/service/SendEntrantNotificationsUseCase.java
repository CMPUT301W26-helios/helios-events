package com.example.helios.service;

import androidx.annotation.NonNull;

import com.example.helios.model.NotificationAudience;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class SendEntrantNotificationsUseCase {
    private final OrganizerNotificationService organizerNotificationService;

    public SendEntrantNotificationsUseCase() {
        this.organizerNotificationService = new OrganizerNotificationService();
    }

    public void execute(
            @NonNull String organizerUid,
            @NonNull String eventId,
            @NonNull NotificationAudience audience,
            @NonNull String title,
            @NonNull String message,
            @NonNull OnSuccessListener<NotificationSendResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        organizerNotificationService.sendToAudience(
                organizerUid,
                eventId,
                audience,
                title,
                message,
                onSuccess,
                onFailure
        );
    }
}
