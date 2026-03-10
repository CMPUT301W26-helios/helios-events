package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;

public class OrganizeFragment extends Fragment {

    private final EventService eventService = new EventService();
    private final ProfileService profileService = new ProfileService();

    public OrganizeFragment() {
        super(R.layout.fragment_organize);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button seedButton = view.findViewById(R.id.button_seed_demo_event);

        seedButton.setOnClickListener(v -> seedDemoEvent());
    }

    private void seedDemoEvent() {
        // Ensure we have a Firebase uid (anonymous auth provides this)
        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();

            Event demo = new Event(
                    "demo_event_dance_elders",              // eventId (doc id)
                    "Dance Class for Elders",               // title
                    "Hosting a great dance class for elders\nTags: Dance", // description (tags folded in)
                    "Edmonton, AB",                         // locationName
                    null,                                   // address
                    1772607600000L,                         // startTimeMillis (Mar 4 2026 00:00 UTC-7)
                    1772611200000L,                         // endTimeMillis (start + 1h)
                    1772002800000L,                         // registrationOpensMillis (Feb 25 2026 00:00 UTC-7)
                    1772607599000L,                         // registrationClosesMillis (Mar 3 2026 23:59:59 UTC-7)
                    50,                                     // capacity (maxEntrants)
                    50,                                     // sampleSize
                    null,                                   // waitlistLimit (optional)
                    false,                                  // geolocationRequired
                    "Random lottery draw after registration closes.", // lotteryGuidelines
                    uid,                                    // organizerUid (current user)
                    null,                                   // posterImageId
                    null                                    // qrCodeValue
            );

            eventService.saveEvent(
                    demo,
                    unused -> Toast.makeText(requireContext(),
                            "Demo event added: " + demo.getEventId(),
                            Toast.LENGTH_SHORT).show(),
                    error -> Toast.makeText(requireContext(),
                            "Failed to add demo event: " + error.getMessage(),
                            Toast.LENGTH_LONG).show()
            );

        }, error -> Toast.makeText(requireContext(),
                "Auth failed: " + error.getMessage(),
                Toast.LENGTH_LONG).show());
    }
}