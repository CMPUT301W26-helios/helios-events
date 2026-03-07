package com.example.helios.ui;

import android.os.Bundle;
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
import com.example.helios.data.EventService;
import com.example.helios.model.Event;

import java.util.ArrayList;
import java.util.List;

public class EventListFragment extends Fragment {

    private RecyclerView rvEvents;
    private EditText etSearch;

    private EventAdapter eventAdapter;
    private EventService eventService;

    // Full list from Firestore
    private final List<Event> allEvents = new ArrayList<>();
    // Displayed list after filtering
    private final List<Event> filteredEvents = new ArrayList<>();

    public EventListFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_screen, container, false);
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

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(filteredEvents);

        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterEvents(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });
    }

    private void loadEvents() {
        eventService.getAllEvents(
                events -> {
                    allEvents.clear();
                    allEvents.addAll(events);

                    filteredEvents.clear();
                    filteredEvents.addAll(events);

                    eventAdapter.notifyDataSetChanged();
                },
                e -> Toast.makeText(requireContext(),
                        "Failed to load events: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show()
        );
    }

    private void filterEvents(String query) {
        filteredEvents.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredEvents.addAll(allEvents);
        } else {
            String lowerQuery = query.toLowerCase().trim();

            for (Event event : allEvents) {
                String title = event.getTitle() != null ? event.getTitle().toLowerCase() : "";
                String description = event.getDescription() != null ? event.getDescription().toLowerCase() : "";

                if (title.contains(lowerQuery) || description.contains(lowerQuery)) {
                    filteredEvents.add(event);
                }
            }
        }

        eventAdapter.notifyDataSetChanged();
    }
}