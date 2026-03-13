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
 * Organizer flow: manage a single event (from My Events).
 * Expects an argument "arg_event_id" with the event's id.
 */
public class ManageEventFragment extends Fragment {

    private final EventService eventService = new EventService();
    @Nullable
    private String eventId;

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
        //Button manageNotificationsButton = view.findViewById(R.id.button_manage_notifications);
        Button viewQrButton = view.findViewById(R.id.button_view_qr_code);
        Button editButton = view.findViewById(R.id.button_edit_event);
        Button mapButton = view.findViewById(R.id.button_show_mapped_location);
        //MaterialButton backButton = view.findViewById(R.id.button_manage_back);

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

        /*if (backButton != null) {
            backButton.setOnClickListener(v -> 
                NavHostFragment.findNavController(this).popBackStack(R.id.organizeFragment, false)
            );
        }*/

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

/*        if (manageNotificationsButton != null) {
            manageNotificationsButton.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString("arg_event_id", eventId);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.notifyEntrantsFragment, args);
            });
        }*/

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
