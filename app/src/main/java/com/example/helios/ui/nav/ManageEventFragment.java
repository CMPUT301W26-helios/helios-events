package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.ui.event.EventDetailsBottomSheet;
import com.google.android.material.button.MaterialButton;

/**
 * Fragment that allows an organizer to manage a single event.
 * Provides access to various management tools like viewing the event page,
 * managing the entrant list, viewing the QR code, and editing event details.
 */
public class ManageEventFragment extends Fragment {

    private final EventService eventService = new EventService();
    @Nullable
    private String eventId;

    /**
     * Default constructor for ManageEventFragment.
     */
    public ManageEventFragment() {
        super(R.layout.fragment_manage_event);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString("arg_event_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView nameView = view.findViewById(R.id.tv_manage_event_name);
        Button viewPageButton = view.findViewById(R.id.button_view_event_page);
        Button entrantListButton = view.findViewById(R.id.button_entrant_list);
        Button viewQrButton = view.findViewById(R.id.button_view_qr_code);
        Button editButton = view.findViewById(R.id.button_edit_event);
        Button mapButton = view.findViewById(R.id.button_show_mapped_location);

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Select an event first.",
                    Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this)
                    .popBackStack(R.id.organizeFragment, false);
            return;
        } else {
            eventService.getEventById(eventId, event -> {
                if (!isAdded() || event == null) return;
                nameView.setText(event.getTitle() != null ? event.getTitle() : "(no title)");
            }, error -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to load event: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            });
        }

        viewPageButton.setOnClickListener(v -> {
            if (eventId != null) {
                // Pass 'true' to hide the Join Waiting List button for organizers
                EventDetailsBottomSheet.newInstance(eventId, true)
                        .show(getParentFragmentManager(), "event_details");
            }
        });

        entrantListButton.setOnClickListener(v -> {
            if (eventId != null) {
                Bundle args = new Bundle();
                args.putString("arg_event_id", eventId);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.organizerViewEntrantsFragment, args);
            }
        });

        viewQrButton.setOnClickListener(v -> {
            if (eventId != null) {
                Bundle args = new Bundle();
                args.putString("arg_event_id", eventId);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.viewEventQrFragment, args);
            }
        });

        mapButton.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "Mapped location view not implemented yet.",
                        Toast.LENGTH_SHORT).show()
        );

        editButton.setOnClickListener(v -> {
            if (eventId != null) {
                Bundle args = new Bundle();
                args.putString("arg_event_id", eventId);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.editEventFragment, args);
            }
        });
    }
}
