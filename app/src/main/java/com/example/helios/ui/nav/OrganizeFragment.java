package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
// import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
// import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.EventAdapter;

import java.util.ArrayList;
import java.util.List;

public class OrganizeFragment extends Fragment {

    private final EventService eventService = new EventService();
    private final ProfileService profileService = new ProfileService();

    private final List<com.example.helios.model.Event> currentEvents = new ArrayList<>();
    private EventAdapter currentAdapter;

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

        RecyclerView rvCurrent = view.findViewById(R.id.rv_current_events);
        rvCurrent.setLayoutManager(new LinearLayoutManager(requireContext()));
        currentAdapter = new EventAdapter(currentEvents, event -> {
            if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                Toast.makeText(requireContext(),
                        "Missing event id.", Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle args = new Bundle();
            args.putString("arg_event_id", event.getEventId());
            NavHostFragment.findNavController(this)
                    .navigate(R.id.manageEventFragment, args);
        });
        rvCurrent.setAdapter(currentAdapter);

        loadOrganizerEvents();
    }

    private void loadOrganizerEvents() {
        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            eventService.getAllEvents(events -> {
                if (!isAdded()) return;
                currentEvents.clear();
                for (com.example.helios.model.Event e : events) {
                    if (uid.equals(e.getOrganizerUid())) {
                        currentEvents.add(e);
                    }
                }
                currentAdapter.notifyDataSetChanged();
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

    /*
    // Previous debug helper to seed a demo event directly from Organizer.
    // Kept here for reference; navigation now goes to the Create Event flow instead.
    private void seedDemoEvent() {
        // Ensure we have a Firebase uid (anonymous auth provides this)
        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();

            com.example.helios.model.Event demo = new com.example.helios.model.Event(
                    "demo_event_dance_elders",              // eventId (doc id)
                    "Dance Class for Elders",               // title
                    "Hosting a great dance class for elders\nTags: Dance", // description
                    "Edmonton, AB",                         // locationName
                    null,                                   // address
                    1772607600000L,                         // startTimeMillis
                    1772611200000L,                         // endTimeMillis
                    1772002800000L,                         // registrationOpensMillis
                    1772607599000L,                         // registrationClosesMillis
                    50,                                     // capacity
                    50,                                     // sampleSize
                    null,                                   // waitlistLimit
                    false,                                  // geolocationRequired
                    "Random lottery draw after registration closes.", // lotteryGuidelines
                    uid,                                    // organizerUid
                    null,                                   // posterImageId
                    null                                    // qrCodeValue
            );

            eventService.saveEvent(
                    demo,
                    unused -> android.widget.Toast.makeText(requireContext(),
                            "Demo event added: " + demo.getEventId(),
                            android.widget.Toast.LENGTH_SHORT).show(),
                    error -> android.widget.Toast.makeText(requireContext(),
                            "Failed to add demo event: " + error.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show()
            );

        }, error -> android.widget.Toast.makeText(requireContext(),
                "Auth failed: " + error.getMessage(),
                android.widget.Toast.LENGTH_LONG).show());
    }
    */
}