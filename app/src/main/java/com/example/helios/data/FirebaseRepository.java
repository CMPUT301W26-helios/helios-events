package com.example.helios.data;

import androidx.annotation.NonNull;

import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void saveUser(UserProfile user,
                         OnSuccessListener<Void> onSuccess,
                         OnFailureListener onFailure) {
        db.collection("users")
                .document(user.getDeviceId())
                .set(user)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void getUser(String deviceId,
                        OnSuccessListener<DocumentSnapshot> onSuccess,
                        OnFailureListener onFailure) {
        db.collection("users")
                .document(deviceId)
                .get()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
