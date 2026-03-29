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
import com.example.helios.service.ImageService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.AdminEventAdapter;
import com.example.helios.ui.AdminImageAdapter;
import com.example.helios.ui.UserAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment for administrative tasks: managing all events, users, and uploaded images.
 * Provides a tabbed interface to switch between Events, Users, and Images.
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

    // Images
    private RecyclerView rvImages;
    private AdminImageAdapter imageAdapter;
    private final ImageService imageService = new ImageService();
    private final List<Event> allImages = new ArrayList<>();
    private final List<Event> displayedImages = new ArrayList<>();
    private TextView tvNoImages;

    // Tabs
    private TextView btnTabEvents;
    private TextView btnTabUsers;
    private TextView btnTabImages;

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
        btnTabImages = view.findViewById(R.id.btn_tab_images);

        // Events RV
        rvEvents = view.findViewById(R.id.rv_events);
        eventAdapter = new AdminEventAdapter(
                displayedEvents,
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
                event -> showOrganizerInfo(event)
        );
        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);

        // Users RV
        rvUsers = view.findViewById(R.id.rv_users);
        userAdapter = new UserAdapter(
                displayedUsers,
                organizerUids,
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
                user -> showUserEvents(user)
        );
        rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUsers.setAdapter(userAdapter);

        // Images RV
        rvImages   = view.findViewById(R.id.rv_images);
        tvNoImages = view.findViewById(R.id.tv_no_images);

        imageAdapter = new AdminImageAdapter(displayedImages, event -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Image")
                    .setMessage("Permanently remove the poster from \""
                            + nonEmptyOr(event.getTitle(), "this event") + "\"? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> deleteImage(event))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        rvImages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvImages.setAdapter(imageAdapter);

        // tab wiring
        btnTabEvents.setOnClickListener(v -> showTab(Tab.EVENTS));
        btnTabUsers.setOnClickListener(v -> showTab(Tab.USERS));
        if (btnTabImages != null) {
            btnTabImages.setOnClickListener(v -> showTab(Tab.IMAGES));
        }

        showTab(Tab.EVENTS);
        loadEvents();
        loadUsers();
        loadImages();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
        loadUsers();
        loadImages();
    }

    // Tab switching
    private enum Tab { EVENTS, USERS, IMAGES }

    /**
     * Switches to the specified tab and refreshes all data sets.
     */
    private void showTab(@NonNull Tab tab) {
        rvEvents.setVisibility(tab == Tab.EVENTS ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(tab == Tab.USERS  ? View.VISIBLE : View.GONE);
        rvImages.setVisibility(tab == Tab.IMAGES ? View.VISIBLE : View.GONE);

        if (tvNoImages != null) {
            if (tab == Tab.IMAGES && displayedImages.isEmpty()) {
                tvNoImages.setVisibility(View.VISIBLE);
            } else {
                tvNoImages.setVisibility(View.GONE);
            }
        }

        if (btnTabEvents != null) btnTabEvents.setAlpha(tab == Tab.EVENTS ? 0.5f : 1f);
        if (btnTabUsers  != null) btnTabUsers.setAlpha(tab  == Tab.USERS  ? 0.5f : 1f);
        if (btnTabImages != null) btnTabImages.setAlpha(tab == Tab.IMAGES ? 0.5f : 1f);

        switch (tab) {
            case EVENTS: loadEvents(); break;
            case USERS:  loadUsers();  break;
            case IMAGES: loadImages(); break;
        }
    }

    //Data loading
    /**
     * Loads all events. Also rebuilds organizerUids and refreshes user cards.
     */
    private void loadEvents() {
        eventService.getAllEvents(events -> {
            if (!isAdded()) return;

            allEvents.clear();
            allEvents.addAll(events);
            displayedEvents.clear();
            displayedEvents.addAll(allEvents);
            eventAdapter.notifyDataSetChanged();

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

    /** Loads all user profiles. */
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

    /** Loads all events that have an uploaded poster image. */
    private void loadImages() {
        imageService.getAllImages(events -> {
            if (!isAdded()) return;
            allImages.clear();
            allImages.addAll(events);
            displayedImages.clear();
            displayedImages.addAll(allImages);
            imageAdapter.notifyDataSetChanged();
            if (tvNoImages != null) {
                tvNoImages.setVisibility(displayedImages.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to load images: " + error.getMessage(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    //Deletion
    private void deleteEvent(@NonNull Event event) {
        eventService.deleteEvent(event.getEventId(),
                unused -> {
                    if (!isAdded()) return;
                    allEvents.remove(event);
                    displayedEvents.remove(event);
                    eventAdapter.notifyDataSetChanged();

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

    private void deleteUser(@NonNull UserProfile user) {
        profileService.deleteProfile(user.getUid(),
                unused -> {
                    if (!isAdded()) return;
                    allUsers.remove(user);
                    displayedUsers.remove(user);

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

    /**
     * Removes the poster URI from an event and updates the local list.
     */
    private void deleteImage(@NonNull Event event) {
        imageService.deleteImage(event,
                unused -> {
                    if (!isAdded()) return;
                    allImages.remove(event);
                    displayedImages.remove(event);
                    imageAdapter.notifyDataSetChanged();
                    if (tvNoImages != null) {
                        tvNoImages.setVisibility(displayedImages.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    Toast.makeText(requireContext(), "Image deleted.", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Delete failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    // dialogs
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

                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Organizer — " + nonEmptyOr(event.getTitle(), "Untitled Event"))
                            .setMessage("Name:   " + name + "\nEmail:  " + email + "\nRole:   " + role)
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

    private void showUserEvents(@NonNull UserProfile user) {
        String uid = user.getUid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User is missing a UID.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Event> userEvents = new ArrayList<>();
        for (Event event : allEvents) {
            if (uid.equals(event.getOrganizerUid())) userEvents.add(event);
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
            sb.append(i + 1).append(". ").append(nonEmptyOr(e.getTitle(), "Untitled Event"));
            if (e.getLocationName() != null && !e.getLocationName().trim().isEmpty()) {
                sb.append("\n").append(e.getLocationName());
            }
            if (i < userEvents.size() - 1) sb.append("\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Events by " + displayName + " (" + userEvents.size() + ")")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    // helpers
    private String nonEmptyOr(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}