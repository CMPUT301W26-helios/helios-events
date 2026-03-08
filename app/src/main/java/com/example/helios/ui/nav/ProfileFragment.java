package com.example.helios.ui.nav;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.helios.R;
import com.example.helios.model.UserProfile;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.ProfileSetupActivity;
import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    private final ProfileService profileService = new ProfileService();

    private TextView tvName;
    private TextView tvEmail;
    private TextView tvPhone;

    private MaterialButton btnEdit;
    private MaterialButton btnDelete;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvName = view.findViewById(R.id.tv_name);
        tvEmail = view.findViewById(R.id.tv_email);
        tvPhone = view.findViewById(R.id.tv_phone);

        btnEdit = view.findViewById(R.id.btn_edit);
        btnDelete = view.findViewById(R.id.btn_delete);

        btnEdit.setOnClickListener(v -> openProfileEditorSafe());
        btnDelete.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, "Delete profile not implemented yet.", Toast.LENGTH_SHORT).show();
            }
        });

        loadProfileSafe();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileSafe();
    }

    private void openProfileEditorSafe() {
        Context ctx = getContext();
        if (ctx == null) return;

        Intent intent = new Intent(ctx, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_MODE, ProfileSetupActivity.MODE_EDIT_PROFILE);
        intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
        startActivity(intent);
    }

    private void loadProfileSafe() {
        Context ctx = getContext();
        if (ctx == null) return;

        // If our view is gone, don't try to update UI
        if (getView() == null) return;

        profileService.loadCurrentProfile(ctx, profile -> {
            // Async callback can return after fragment is detached
            if (!isAdded() || getView() == null) return;

            if (profile == null) {
                bindEmptyProfile();
                return;
            }

            bindProfile(profile);

        }, error -> {
            if (!isAdded() || getContext() == null) return;
            Toast.makeText(getContext(),
                    "Failed to load profile: " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void bindEmptyProfile() {
        tvName.setText("Name: (not set)");
        tvEmail.setText("Email: (not set)");
        tvPhone.setVisibility(View.GONE);
    }

    private void bindProfile(@NonNull UserProfile profile) {
        String name = normalize(profile.getName());
        String email = normalize(profile.getEmail());
        String phone = normalize(profile.getPhone());

        tvName.setText("Name: " + (name != null ? name : "(not set)"));
        tvEmail.setText("Email: " + (email != null ? email : "(not set)"));

        if (phone != null) {
            tvPhone.setVisibility(View.VISIBLE);
            tvPhone.setText("Phone (Optional): " + phone);
        } else {
            tvPhone.setVisibility(View.GONE);
        }
    }

    @Nullable
    private String normalize(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}