package com.example.helios.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.auth.DeviceIdProvider;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.UserProfile;
import com.google.firebase.firestore.DocumentSnapshot;

public class HomepageActivity extends AppCompatActivity {
    private TextView tvWelcome, tvEmail;
    private final FirebaseRepository userRepo = new FirebaseRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvWelcome = findViewById(R.id.welcome);
        tvEmail = findViewById(R.id.email);

        loadUser();
    }

    private void loadUser() {
        String deviceId = DeviceIdProvider.getDeviceId(this);

        userRepo.getUser(deviceId,
                this::handleUserDoc,
                e -> Toast.makeText(this, "Failed to load user: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void handleUserDoc(DocumentSnapshot doc) {
        if (!doc.exists()) {
            tvWelcome.setText("No user profile found.");
            tvEmail.setText("");
            return;
        }

        UserProfile user = doc.toObject(UserProfile.class);
        if (user == null) return;

        tvWelcome.setText("Welcome " + user.getName() + "!");
        tvEmail.setText("Email: " + user.getEmail());
    }    
}
