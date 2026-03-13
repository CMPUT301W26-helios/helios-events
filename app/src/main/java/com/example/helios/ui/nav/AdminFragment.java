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
import com.example.helios.model.Event;
import com.example.helios.model.UserProfile;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.AdminEventAdapter;
import com.example.helios.ui.UserAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for administrative tasks, such as managing all events and users.
 * Provides a tabbed interface to switch between event and user management.
 */
public class AdminFragment extends Fragment {

    // Events
    private RecyclerView rvEvents;
    private AdminEventAdapter eventAdapter;
    private final EventService eventService = new EventService();
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> displayedEvents = new ArrayList<>();

    // Users
    private RecyclerView rvUsers;
    private UserAdapter userAdapter;
    private final ProfileService profileService = new ProfileService();
    private final List<UserProfile> allUsers = new ArrayList<>();
    private final List<UserProfile> displayedUsers = new ArrayList<>();

    // Tabs
    private TextView btnTabEvents;
    private TextView btnTabUsers;

    /**
     * Default constructor for AdminFragment.
     */
    public AdminFragment() {
        super(R.layout.fragment_admin);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnTabEvents = view.findViewById(R.id.btn_tab_events);
        btnTabUsers  = view.findViewById(R.id.btn_tab_users);

        // Events RV
        rvEvents = view.findViewById(R.id.rv_events);
        eventAdapter = new AdminEventAdapter(displayedEvents, event -> {
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

        // Tab click listeners
        btnTabEvents.setOnClickListener(v -> showTab(true));
        btnTabUsers.setOnClickListener(v -> showTab(false));

        // Start on Events tab
        showTab(true);

        loadEvents();
        loadUsers();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
        loadUsers();
    }

    /**
     * Switches between the Events tab and the Users tab.
     *
     * @param showEvents True to show the events list, false to show the users list.
     */
    private void showTab(boolean showEvents) {
        rvEvents.setVisibility(showEvents ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(showEvents ? View.GONE : View.VISIBLE);
        btnTabEvents.setAlpha(showEvents ? 0.5f : 1f);
        btnTabUsers.setAlpha(showEvents ?  1f : .5f);
    }

    /**
     * Loads all events from the {@link EventService}.
     */
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

    /**
     * Deletes a specific event.
     *
     * @param event The event object to delete.
     */
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

    /**
     * Loads all user profiles from the {@link ProfileService}.
     */
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

    /**
     * Deletes a specific user profile.
     *
     * @param user The user profile object to delete.
     */
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
