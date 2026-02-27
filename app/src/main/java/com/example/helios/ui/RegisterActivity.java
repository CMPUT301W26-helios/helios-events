package com.example.helios.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.helios.R;
import com.example.helios.auth.AuthDeviceService;


public class RegisterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If already registered, skip to Home
        if (AuthDeviceService.isRegistered(this)) {
            startActivity(new Intent(this, HomepageActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_register);

        Button registerBtn = findViewById(R.id.button_register);
        registerBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });
    }    
}
