package com.example.helios.ui.nav;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.ui.EventAdapter;
import com.example.helios.ui.event.EventDetailsBottomSheet;

import java.util.ArrayList;
import java.util.List;

public class EventsFragment extends Fragment {

    private RecyclerView rvEvents;
    private EditText etSearch;

    private EventAdapter eventAdapter;
    private EventService eventService;

    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> filteredEvents = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    private boolean loadedOnce = false;

    public EventsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvEvents = view.findViewById(R.id.rv_events);
        etSearch = view.findViewById(R.id.et_search);

        eventService = new EventService();

        setupRecyclerView();
        setupSearch();
        loadEvents();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh list after returning from other screens (optional, but useful during dev)
        if (loadedOnce) {
            loadEvents();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingFilter != null) {
            handler.removeCallbacks(pendingFilter);
            pendingFilter = null;
        }
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(filteredEvents, event -> {
            if (!isAdded()) return;

            String eventId = event.getEventId();
            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Event is missing an ID.", Toast.LENGTH_SHORT).show();
                return;
            }

            EventDetailsBottomSheet
                    .newInstance(eventId)
                    .show(getParentFragmentManager(), "event_details");
        });

        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String query = s != null ? s.toString() : "";

                if (pendingFilter != null) {
                    handler.removeCallbacks(pendingFilter);
                }

                pendingFilter = () -> filterEvents(query);
                handler.postDelayed(pendingFilter, 150);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadEvents() {
        eventService.getAllEvents(
                events -> {
                    if (!isAdded()) return;

                    loadedOnce = true;

                    allEvents.clear();
                    allEvents.addAll(events);

                    String currentQuery = etSearch.getText() != null ? etSearch.getText().toString() : "";
                    filterEvents(currentQuery);
                },
                e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Failed to load events: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void filterEvents(@Nullable String query) {
        filteredEvents.clear();

        String q = query == null ? "" : query.trim().toLowerCase();

        if (q.isEmpty()) {
            filteredEvents.addAll(allEvents);
        } else {
            for (Event event : allEvents) {
                if (eventMatches(event, q)) {
                    filteredEvents.add(event);
                }
            }
        }

        eventAdapter.notifyDataSetChanged();
    }

    private boolean eventMatches(@NonNull Event event, @NonNull String q) {
        String title = safeLower(event.getTitle());
        String desc = safeLower(event.getDescription());
        String loc = safeLower(event.getLocationName());

        return title.contains(q) || desc.contains(q) || loc.contains(q);
    }

    private String safeLower(@Nullable String s) {
        return s == null ? "" : s.toLowerCase();
    }
}