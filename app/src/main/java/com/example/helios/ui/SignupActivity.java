package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.auth.DeviceIdProvider;
import com.example.helios.auth.AuthDeviceService;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.UserProfile;
import com.google.android.material.textfield.TextInputEditText;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText editTextName, editTextEmail;
    private Button buttonConfirm;

    private final FirebaseRepository userRepository = new FirebaseRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        editTextName = findViewById(R.id.name);
        editTextEmail = findViewById(R.id.email);
        buttonConfirm = findViewById(R.id.confirm_button);

        buttonConfirm.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {

        String name = editTextName.getText() != null ?
                editTextName.getText().toString().trim() : "";

        String email = editTextEmail.getText() != null ?
                editTextEmail.getText().toString().trim() : "";

        // Validate name
        if (TextUtils.isEmpty(name)) {
            editTextName.setError("Enter name");
            editTextName.requestFocus();
            return;
        }

        // Validate email
        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Enter email");
            editTextEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Enter valid email");
            editTextEmail.requestFocus();
            return;
        }

        // Get device ID
        String deviceId = DeviceIdProvider.getDeviceId(this);

        // Create user object
        UserProfile user = new UserProfile(deviceId, name, email);

        // Save to Firestore
        userRepository.saveUser(user,
                unused -> {
                    // Mark as registered locally
                    AuthDeviceService.setRegistered(this, true);

                    // Go to Home
                    Intent intent = new Intent(SignupActivity.this, HomepageActivity.class);
                    startActivity(intent);
                    finish();
                },
                e -> Toast.makeText(SignupActivity.this,
                        "Error saving user: " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }
}