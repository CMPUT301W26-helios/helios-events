package com.example.helios.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;public class AuthDeviceService {
    private final FirebaseAuth auth;

    public AuthDeviceService() {
        this.auth = FirebaseAuth.getInstance();
    }

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

    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void signOut() {
        auth.signOut();
    }
}
