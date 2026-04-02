package com.example.helios.ui.nav;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.EventAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment that allows organizers to view and manage their events.
 * It separates events into current and past categories and provides search functionality.
 */
public class OrganizeFragment extends Fragment {

    private final EventService eventService = new EventService();
    private final ProfileService profileService = new ProfileService();
    private final WaitingListService waitingListService = new WaitingListService();

    private final List<com.example.helios.model.Event> allOrganizerEvents = new ArrayList<>();
    private final List<com.example.helios.model.Event> currentEvents = new ArrayList<>();
    private final List<com.example.helios.model.Event> pastEvents = new ArrayList<>();

    private EventAdapter currentAdapter;
    private EventAdapter pastAdapter;
    @Nullable
    private String organizerUid;

    private EditText searchEditText;
    private TextView currentEmptyText;
    private TextView pastEmptyText;

    /**
     * Default constructor for OrganizeFragment.
     */
    public OrganizeFragment() {
        super(R.layout.fragment_organize);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button seedButton = view.findViewById(R.id.button_seed_demo_event);
        seedButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.createEventFragment)
        );

        searchEditText = view.findViewById(R.id.et_search_my_events);
        currentEmptyText = view.findViewById(R.id.tv_current_events_empty);
        pastEmptyText = view.findViewById(R.id.tv_past_events_empty);

        RecyclerView rvCurrent = view.findViewById(R.id.rv_current_events);
        rvCurrent.setLayoutManager(new LinearLayoutManager(requireContext()));
        currentAdapter = new EventAdapter(
                currentEvents,
                this::openManagedEvent,
                new EventAdapter.OnCoOrganizerInviteActionListener() {
                    @Override
                    public void onAcceptInvite(@NonNull Event event) {
                        acceptCoOrganizerInvite(event);
                    }

                    @Override
                    public void onDeclineInvite(@NonNull Event event) {
                        declineCoOrganizerInvite(event);
                    }
                }
        );
        rvCurrent.setAdapter(currentAdapter);

        RecyclerView rvPast = view.findViewById(R.id.rv_past_events);
        if (rvPast != null) {
            rvPast.setLayoutManager(new LinearLayoutManager(requireContext()));
            pastAdapter = new EventAdapter(
                    pastEvents,
                    this::openManagedEvent,
                    null
            );
            rvPast.setAdapter(pastAdapter);
        }

        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyOrganizerFilter(s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        loadOrganizerEvents();
    }

    /**
     * Loads events created by the current organizer from {@link EventService}.
     */
    private void loadOrganizerEvents() {
        profileService.ensureSignedIn(firebaseUser -> {
            organizerUid = firebaseUser.getUid();
            if (currentAdapter != null) {
                currentAdapter.setOrganizerViewerUid(organizerUid);
            }
            if (pastAdapter != null) {
                pastAdapter.setOrganizerViewerUid(organizerUid);
            }

            eventService.getAllEvents(events -> {
                if (!isAdded()) return;

                allOrganizerEvents.clear();
                for (com.example.helios.model.Event e : events) {
                    if (isManagedByCurrentOrganizer(e)) {
                        allOrganizerEvents.add(e);
                    }
                }

                String query = searchEditText != null && searchEditText.getText() != null
                        ? searchEditText.getText().toString()
                        : "";

                applyOrganizerFilter(query);

            }, error -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to load organizer events: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            });

        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Auth failed: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    private boolean isManagedByCurrentOrganizer(@NonNull com.example.helios.model.Event event) {
        return organizerUid != null
                && (organizerUid.equals(event.getOrganizerUid())
                || event.isCoOrganizer(organizerUid)
                || event.isPendingCoOrganizer(organizerUid));
    }

    /**
     * Filters the organizer's events based on a search query and categorizes them into current and past events.
     *
     * @param query The search query string.
     */
    private void applyOrganizerFilter(@NonNull String query) {
        currentEvents.clear();
        pastEvents.clear();

        String normalizedQuery = query.trim().toLowerCase(Locale.CANADA);
        long now = System.currentTimeMillis();

        for (com.example.helios.model.Event event : allOrganizerEvents) {
            if (!matchesQuery(event, normalizedQuery)) {
                continue;
            }

            if (organizerUid != null && event.isPendingCoOrganizer(organizerUid)) {
                currentEvents.add(event);
                continue;
            }

            long endTime = event.getEndTimeMillis();
            if (endTime > 0 && endTime < now) {
                pastEvents.add(event);
            } else {
                currentEvents.add(event);
            }
        }

        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }
        if (pastAdapter != null) {
            pastAdapter.notifyDataSetChanged();
        }

        updateEmptyStates();
    }

    /**
     * Checks if an event matches the search query.
     *
     * @param event The event to check.
     * @param query The normalized search query.
     * @return True if the event matches the query, false otherwise.
     */
    private boolean matchesQuery(com.example.helios.model.Event event, @NonNull String query) {
        if (query.isEmpty()) {
            return true;
        }

        String title = safeLower(event.getTitle());
        String description = safeLower(event.getDescription());
        String location = safeLower(event.getLocationName());
        String address = safeLower(event.getAddress());

        return title.contains(query)
                || description.contains(query)
                || location.contains(query)
                || address.contains(query);
    }

    /**
     * Safely converts a string to lowercase.
     *
     * @param value The string to convert.
     * @return The lowercase string, or an empty string if the input was null.
     */
    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.CANADA);
    }

    /**
     * Updates the visibility of the "empty" text views based on whether the event lists are empty.
     */
    private void updateEmptyStates() {
        if (currentEmptyText != null) {
            currentEmptyText.setVisibility(currentEvents.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (pastEmptyText != null) {
            pastEmptyText.setVisibility(pastEvents.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void openManagedEvent(@NonNull Event event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(),
                    "Missing event id.", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString("arg_event_id", event.getEventId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.manageEventFragment, args);
    }

    private void acceptCoOrganizerInvite(@NonNull Event event) {
        if (organizerUid == null || event.getEventId() == null) return;

        List<String> pending = event.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(organizerUid)) {
            Toast.makeText(requireContext(),
                    "Invite no longer exists.", Toast.LENGTH_SHORT).show();
            loadOrganizerEvents();
            return;
        }

        List<String> coOrganizers = event.getCoOrganizerUids();
        if (coOrganizers == null) coOrganizers = new ArrayList<>();
        if (!coOrganizers.contains(organizerUid)) {
            coOrganizers.add(organizerUid);
        }

        event.setPendingCoOrganizerUids(pending);
        event.setCoOrganizerUids(coOrganizers);

        eventService.saveEvent(event, unused -> {
            if (!isAdded()) return;
            waitingListService.removeEntry(event.getEventId(), organizerUid, unused2 -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Co-organizer invite accepted!", Toast.LENGTH_SHORT).show();
                loadOrganizerEvents();
            }, error -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Accepted, but could not remove waiting list entry: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                loadOrganizerEvents();
            });
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to accept invite: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void declineCoOrganizerInvite(@NonNull Event event) {
        if (organizerUid == null) return;

        List<String> pending = event.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(organizerUid)) {
            Toast.makeText(requireContext(),
                    "Invite no longer exists.", Toast.LENGTH_SHORT).show();
            loadOrganizerEvents();
            return;
        }

        event.setPendingCoOrganizerUids(pending);
        eventService.saveEvent(event, unused -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Co-organizer invite declined.", Toast.LENGTH_SHORT).show();
            loadOrganizerEvents();
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to decline invite: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }
}
