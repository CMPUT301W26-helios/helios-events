package com.example.helios.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Service class handling device authentication using Firebase Auth.
 * Primarily handles anonymous sign-in to associate a user with a specific device.
 */
public class AuthDeviceService {
    private final FirebaseAuth auth;

    /**
     * Initializes the AuthDeviceService with a FirebaseAuth instance.
     */
    public AuthDeviceService() {
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Ensures the user is signed in. If not already signed in, attempts an anonymous sign-in.
     *
     * @param onSuccess Callback receiving the signed-in FirebaseUser.
     * @param onFailure Callback for failed authentication.
     */
    public void ensureSignedIn(
            @NonNull OnSuccessListener<FirebaseUser> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            onSuccess.onSuccess(currentUser);
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        onFailure.onFailure(
                                new IllegalStateException("Anonymous sign-in succeeded but user is null.")
                        );
                        return;
                    }
                    onSuccess.onSuccess(user);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns the currently signed-in FirebaseUser.
     *
     * @return The current FirebaseUser, or null if not signed in.
     */
    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Returns the UID of the currently signed-in user.
     *
     * @return The current user's UID, or null if not signed in.
     */
    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Signs out the current user from Firebase Auth.
     */
    public void signOut() {
        auth.signOut();
    }
}
