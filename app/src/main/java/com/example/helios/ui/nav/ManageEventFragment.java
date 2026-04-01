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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.LotteryService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.event.EventDetailsBottomSheet;

public class ManageEventFragment extends Fragment {

    private final EventService eventService = new EventService();
    private final LotteryService lotteryService = new LotteryService();
    private final ProfileService profileService = new ProfileService();

    @Nullable private String eventId;
    @Nullable private Event loadedEvent;

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView nameView = view.findViewById(R.id.tv_manage_event_name);
        Button viewPageButton = view.findViewById(R.id.button_view_event_page);
        Button entrantListButton = view.findViewById(R.id.button_entrant_list);
        Button runLotteryButton = view.findViewById(R.id.button_run_lottery);
        Button invitePrivateEntrantsButton = view.findViewById(R.id.button_invite_private_entrants);
        Button assignCoOrganizerButton = view.findViewById(R.id.button_assign_coorganizer);
        Button editButton = view.findViewById(R.id.button_edit_event);
        Button mapButton = view.findViewById(R.id.button_show_mapped_location);

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(requireContext(),
                    "Select an event first.", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this)
                    .popBackStack(R.id.organizeFragment, false);
            return;
        }

        eventService.getEventById(eventId, event -> {
            if (!isAdded() || event == null) return;
            loadedEvent = event;
            nameView.setText(event.getTitle() != null ? event.getTitle() : "(no title)");
            invitePrivateEntrantsButton.setVisibility(event.isPrivateEvent() ? View.VISIBLE : View.GONE);

            // Show draw status
            if (event.isDrawHappened()) {
                runLotteryButton.setText("Draw Already Run");
                runLotteryButton.setEnabled(false);
            }
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to load event: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });

        viewPageButton.setOnClickListener(v -> {
            if (eventId != null) {
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

        runLotteryButton.setOnClickListener(v -> showLotteryConfirmDialog());

        invitePrivateEntrantsButton.setOnClickListener(v -> {
            if (eventId == null) return;
            Bundle args = new Bundle();
            args.putString("arg_event_id", eventId);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.privateEventInviteFragment, args);
        });

        assignCoOrganizerButton.setOnClickListener(v -> {
            if (eventId == null) return;
            Bundle args = new Bundle();
            args.putString("arg_event_id", eventId);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.assignCoOrganizerFragment, args);
        });

        mapButton.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "Mapped location view not implemented yet.",
                        Toast.LENGTH_SHORT).show());

        editButton.setOnClickListener(v -> {
            if (eventId != null) {
                Bundle args = new Bundle();
                args.putString("arg_event_id", eventId);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.editEventFragment, args);
            }
        });
    }

    private void showLotteryConfirmDialog() {
        if (loadedEvent == null) {
            Toast.makeText(requireContext(),
                    "Event not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Run Lottery Draw")
                .setMessage("This will randomly select entrants from the waiting list and notify them. This cannot be undone.")
                .setPositiveButton("Run Draw", (dialog, which) -> runLottery())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runLottery() {
        if (loadedEvent == null) return;

        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            lotteryService.runDraw(uid, loadedEvent,
                    unused -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                "Lottery draw complete!", Toast.LENGTH_SHORT).show();
                        View v = getView();
                        if (v != null) {
                            Button btn = v.findViewById(R.id.button_run_lottery);
                            if (btn != null) {
                                btn.setText("Draw Already Run");
                                btn.setEnabled(false);
                            }
                        }
                    },
                    error -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                "Lottery failed: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        }, error -> Toast.makeText(requireContext(),
                "Auth failed: " + error.getMessage(),
                Toast.LENGTH_LONG).show());
    }
}