package com.example.helios.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Repository interface for user profile persistence operations.
 */
public interface UserRepository {

    void saveUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void updateUser(
            @NonNull UserProfile user,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<UserProfile> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void saveFcmToken(
            @NonNull String uid,
            @Nullable String token,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteUser(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void muteNotifications(
            @NonNull String uid,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void setNotificationsMuted(
            @NonNull String uid,
            boolean muted,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void isAdminInstallation(
            @NonNull String installationId,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getAllUsers(
            @NonNull OnSuccessListener<List<UserProfile>> onSuccess,
            @NonNull OnFailureListener onFailure
    );
}
