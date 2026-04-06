package com.example.helios.data;

import androidx.annotation.NonNull;

import com.example.helios.model.NotificationRecord;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * Repository interface for notification persistence operations.
 */
public interface NotificationRepository {

    void saveNotification(
            @NonNull NotificationRecord record,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void saveNotificationsBatch(
            @NonNull List<NotificationRecord> records,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getNotificationsForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    @NonNull
    ListenerRegistration subscribeNotificationsForUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getAllNotifications(
            @NonNull OnSuccessListener<List<NotificationRecord>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteNotification(
            @NonNull String notificationId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );
}
