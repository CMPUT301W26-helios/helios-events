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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * UIDs of users who have organized at least one event.
     * Built from allEvents and passed to the UserAdapter for role-label display.
     */
    private final Set<String> organizerUids = new HashSet<>();

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
        eventAdapter = new AdminEventAdapter(
                displayedEvents,
                // Delete button
                event -> {
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
                },
                // View Organizer button
                event -> showOrganizerInfo(event)
        );
        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);

        // Users RV : adapter is created once; organizerUids is populated after events load.
        rvUsers = view.findViewById(R.id.rv_users);
        userAdapter = new UserAdapter(
                displayedUsers,
                organizerUids,
                // Delete button
                user -> {
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
                },
                // View Events button
                user -> showUserEvents(user)
        );
        rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUsers.setAdapter(userAdapter);

        btnTabEvents.setOnClickListener(v -> showTab(true));
        btnTabUsers.setOnClickListener(v -> showTab(false));

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

    // showOrganizerInfo and showUserEvents are from Anthropic, Claude, 2026-03-14
    // Prompted to implement functionality for the "view organizer" / "view events"
    // buttons in admin_item_event.xml and admin_item_user.xml, respectively

    // ORGANIZER INFO

    /**
     * Fetches the organizer profile for the given event and displays their details in a dialog.
     *
     * @param event The event whose organizer should be displayed.
     */
    private void showOrganizerInfo(@NonNull Event event) {
        String organizerUid = event.getOrganizerUid();
        if (organizerUid == null || organizerUid.trim().isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Organizer Info")
                    .setMessage("This event has no organizer on record.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        profileService.getUserProfile(organizerUid,
                profile -> {
                    if (!isAdded()) return;

                    String name  = profile != null && profile.getName()  != null ? profile.getName()  : "(not set)";
                    String email = profile != null && profile.getEmail() != null ? profile.getEmail() : "(not set)";
                    String role  = profile != null && profile.getRole()  != null ? profile.getRole()  : "user";

                    String message = "Name:   " + name
                            + "\nEmail:  " + email
                            + "\nRole:   " + role;

                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Organizer — " + nonEmptyOr(event.getTitle(), "Untitled Event"))
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                },
                error -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Failed to load organizer: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // USER EVENTS

    /**
     * Shows a dialog listing all events created by the given user.
     * If the user has no events, an appropriate message is shown instead.
     *
     * @param user The user whose events should be displayed.
     */
    private void showUserEvents(@NonNull UserProfile user) {
        String uid = user.getUid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User is missing a UID.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Event> userEvents = new ArrayList<>();
        for (Event event : allEvents) {
            if (uid.equals(event.getOrganizerUid())) {
                userEvents.add(event);
            }
        }

        String displayName = user.getName() != null ? user.getName() : "(no name)";

        if (userEvents.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Events by " + displayName)
                    .setMessage("This user has not created any events.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < userEvents.size(); i++) {
            Event e = userEvents.get(i);
            sb.append(i + 1).append(". ")
                    .append(nonEmptyOr(e.getTitle(), "Untitled Event"));
            if (e.getLocationName() != null && !e.getLocationName().trim().isEmpty()) {
                sb.append("\n").append(e.getLocationName());
            }
            if (i < userEvents.size() - 1) {
                sb.append("\n");
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Events by " + displayName + " (" + userEvents.size() + ")")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    // Tab switching

    /**
     * Switches between the Events tab and the Users tab, and refreshes both
     * data sets so changes on one tab are immediately visible on the other.
     *
     * @param showEvents True to show the events list, false to show the users list.
     */
    private void showTab(boolean showEvents) {
        rvEvents.setVisibility(showEvents ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(showEvents ? View.GONE : View.VISIBLE);
        btnTabEvents.setAlpha(showEvents ? 0.5f : 1f);
        btnTabUsers.setAlpha(showEvents ? 1f : .5f);

        // Refresh data every time the tab is switched so deletions and other
        // changes made on one tab are immediately reflected on the other.
        loadEvents();
        loadUsers();
    }

    // Data loading

    /**
     * Loads all events from the {@link EventService}.
     * Also rebuilds the organizerUids set and refreshes the user adapter so role
     * labels stay in sync whenever events are reloaded.
     */
    private void loadEvents() {
        eventService.getAllEvents(events -> {
            if (!isAdded()) return;

            allEvents.clear();
            allEvents.addAll(events);
            displayedEvents.clear();
            displayedEvents.addAll(allEvents);
            eventAdapter.notifyDataSetChanged();

            // Rebuild the organizer set and refresh user cards.
            organizerUids.clear();
            for (Event e : allEvents) {
                if (e.getOrganizerUid() != null && !e.getOrganizerUid().trim().isEmpty()) {
                    organizerUids.add(e.getOrganizerUid());
                }
            }
            userAdapter.notifyDataSetChanged();

        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to load events: " + error.getMessage(),
                    Toast.LENGTH_SHORT).show();
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

                    // Keep organizerUids accurate after deletion.
                    organizerUids.clear();
                    for (Event e : allEvents) {
                        if (e.getOrganizerUid() != null && !e.getOrganizerUid().trim().isEmpty()) {
                            organizerUids.add(e.getOrganizerUid());
                        }
                    }
                    userAdapter.notifyDataSetChanged();

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
     * Deletes a specific user profile (and their events, via ProfileService).
     *
     * @param user The user profile object to delete.
     */
    private void deleteUser(UserProfile user) {
        profileService.deleteProfile(user.getUid(),
                unused -> {
                    if (!isAdded()) return;
                    allUsers.remove(user);
                    displayedUsers.remove(user);

                    // Remove their events from local lists too.
                    String uid = user.getUid();
                    allEvents.removeIf(e -> uid.equals(e.getOrganizerUid()));
                    displayedEvents.removeIf(e -> uid.equals(e.getOrganizerUid()));
                    organizerUids.remove(uid);

                    userAdapter.notifyDataSetChanged();
                    eventAdapter.notifyDataSetChanged();

                    Toast.makeText(requireContext(), "User deleted.", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Delete failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String nonEmptyOr(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}