package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.UserProfile;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.EventAdapter;
import com.example.helios.ui.UserAdapter;

import java.util.ArrayList;
import java.util.List;

public class AdminFragment extends Fragment {

    // Events
    private RecyclerView rvEvents;
    private EventAdapter eventAdapter;
    private final EventService eventService = new EventService();
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> displayedEvents = new ArrayList<>();

    // Users
    private RecyclerView rvUsers;
    private UserAdapter userAdapter;
    private final ProfileService profileService = new ProfileService();
    private final List<UserProfile> allUsers = new ArrayList<>();
    private final List<UserProfile> displayedUsers = new ArrayList<>();

    public AdminFragment() {
        super(R.layout.fragment_admin);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Events RV
        rvEvents = view.findViewById(R.id.rv_events);
        eventAdapter = new EventAdapter(displayedEvents, event -> {
            String eventId = event.getEventId();
            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Event is missing an ID.", Toast.LENGTH_SHORT).show();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Event")
                    .setMessage("Are you sure you want to delete \"" + event.getTitle() + "\"? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteEvent(event))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);

        // Users RV
        rvUsers = view.findViewById(R.id.rv_users);
        userAdapter = new UserAdapter(displayedUsers, user -> {
            String currentUid = new com.example.helios.auth.AuthDeviceService().getCurrentUid();
            if (user.getUid() != null && user.getUid().equals(currentUid)) {
                Toast.makeText(requireContext(), "You cannot delete your own account.", Toast.LENGTH_SHORT).show();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete User")
                    .setMessage("Are you sure you want to delete \"" + user.getName() + "\"? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteUser(user))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUsers.setAdapter(userAdapter);

        loadEvents();
        loadUsers();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
        loadUsers();
    }

    // ── Events ───────────────────────────────────────────────────────────────

    private void loadEvents() {
        eventService.getAllEvents(events -> {
            if (!isAdded()) return;
            allEvents.clear();
            allEvents.addAll(events);
            displayedEvents.clear();
            displayedEvents.addAll(allEvents);
            eventAdapter.notifyDataSetChanged();
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to load events: " + error.getMessage(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteEvent(Event event) {
        eventService.deleteEvent(event.getEventId(),
                unused -> {
                    if (!isAdded()) return;
                    allEvents.remove(event);
                    displayedEvents.remove(event);
                    eventAdapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), "Event deleted.", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Delete failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ── Users ────────────────────────────────────────────────────────────────

    private void loadUsers() {
        profileService.getAllProfiles(users -> {
            if (!isAdded()) return;
            allUsers.clear();
            allUsers.addAll(users);
            displayedUsers.clear();
            displayedUsers.addAll(allUsers);
            userAdapter.notifyDataSetChanged();
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to load users: " + error.getMessage(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteUser(UserProfile user) {
        profileService.deleteProfile(user.getUid(),
                unused -> {
                    if (!isAdded()) return;
                    allUsers.remove(user);
                    displayedUsers.remove(user);
                    userAdapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), "User deleted.", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Delete failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}