package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.data.NotificationRepository;
import com.example.helios.model.NotificationRecord;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.MainActivity;
import com.example.helios.ui.NotificationAdapter;
import com.example.helios.ui.common.HeliosUi;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private ProfileService profileService;
    private NotificationRepository notificationRepository;

    private RecyclerView rvNotifications;
    private TextView tvNoNotifications;
    private NotificationAdapter adapter;
    private final List<NotificationRecord> notifications = new ArrayList<>();

    public NotificationsFragment() { super(R.layout.fragment_notifications); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView tvHeaderTitle = view.findViewById(R.id.tvScreenTitle);
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText("Notifications");
        }

        HeliosApplication application = HeliosApplication.from(requireContext());
        profileService = application.getProfileService();
        notificationRepository = application.getNotificationRepository();
        rvNotifications = view.findViewById(R.id.rv_notifications);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications);

        adapter = new NotificationAdapter(notifications, this::openNotificationTarget);
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
            notificationRepository.getNotificationsForUser(uid, records -> {
                if (!isAdded()) return;
                notifications.clear();
                notifications.addAll(records);
                adapter.replaceNotifications(notifications);
                tvNoNotifications.setVisibility(
                        notifications.isEmpty() ? View.VISIBLE : View.GONE);
            }, e -> {
                if (!isAdded()) return;
                HeliosUi.toast(this, "Failed to load notifications.");
            });
        }, e -> {
            if (!isAdded()) return;
            HeliosUi.toast(this, "Not signed in.");
        });
    }

    private void openNotificationTarget(@NonNull NotificationRecord record) {
        if (!isAdded()) {
            return;
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openNotificationTarget(record.getEventId());
        }
    }
}
