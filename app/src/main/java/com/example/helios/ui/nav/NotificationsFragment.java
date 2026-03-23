package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.NotificationRecord;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.NotificationAdapter;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private final ProfileService profileService = new ProfileService();
    private final FirebaseRepository repository = new FirebaseRepository();

    private RecyclerView rvNotifications;
    private TextView tvNoNotifications;
    private NotificationAdapter adapter;
    private final List<NotificationRecord> notifications = new ArrayList<>();

    public NotificationsFragment() { super(R.layout.fragment_notifications); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvNotifications = view.findViewById(R.id.rv_notifications);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications);

        adapter = new NotificationAdapter(notifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotifications.setAdapter(adapter);

        loadNotifications();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        if (getContext() == null) return;

        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            repository.getNotificationsForUser(uid, records -> {
                if (!isAdded()) return;
                notifications.clear();
                notifications.addAll(records);
                adapter.notifyDataSetChanged();
                tvNoNotifications.setVisibility(
                        notifications.isEmpty() ? View.VISIBLE : View.GONE);
            }, e -> {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(),
                        "Failed to load notifications.", Toast.LENGTH_SHORT).show();
            });
        }, e -> {
            if (!isAdded() || getContext() == null) return;
            Toast.makeText(getContext(), "Not signed in.", Toast.LENGTH_SHORT).show();
        });
    }
}