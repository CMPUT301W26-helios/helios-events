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
import com.example.helios.service.EventService;
import com.example.helios.ui.EventAdapter;
import com.example.helios.ui.event.EventDetailsBottomSheet;

import java.util.ArrayList;
import java.util.List;

public class AdminFragment extends Fragment {

    private RecyclerView rvEvents;
    private EventAdapter eventAdapter;
    private final EventService eventService = new EventService();

    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> displayedEvents = new ArrayList<>();

    public AdminFragment() {
        super(R.layout.fragment_admin);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        loadEvents();
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

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
    }

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
}