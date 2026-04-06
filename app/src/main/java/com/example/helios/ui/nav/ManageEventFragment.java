package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.service.EventService;
import com.example.helios.service.OrganizerNotificationService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.event.EventDetailsBottomSheet;
import com.example.helios.ui.event.EventUiFormatter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ManageEventFragment extends Fragment {
    private EventService eventService;
    private ProfileService profileService;
    private WaitingListService waitingListService;
    private OrganizerNotificationService organizerNotificationService;

    @Nullable private String eventId;
    @Nullable private Event loadedEvent;
    @Nullable private String currentUserUid;
    @Nullable private com.example.helios.model.UserProfile currentUserProfile;
    private boolean openEventPostingOnLoad;

    public ManageEventFragment() {
        super(R.layout.fragment_manage_event);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        profileService = application.getProfileService();
        waitingListService = application.getWaitingListService();
        organizerNotificationService = application.getOrganizerNotificationService();
        Bundle args = getArguments();
        eventId = EventNavArgs.getEventId(args);
        openEventPostingOnLoad = EventNavArgs.shouldOpenEventPosting(args);
        if (args != null) {
            args.remove(EventNavArgs.ARG_OPEN_EVENT_POSTING);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton backButton = view.findViewById(R.id.submenu_back_button);
        TextView titleView = view.findViewById(R.id.submenu_title);
        TextView subtitleView = view.findViewById(R.id.submenu_subtitle);
        TextView nameView = view.findViewById(R.id.tv_manage_event_name);
        TextView summaryView = view.findViewById(R.id.tv_manage_event_summary);
        TextView statusView = view.findViewById(R.id.tv_manage_event_status);
        MaterialButton viewPageButton = view.findViewById(R.id.button_view_event_page);
        MaterialButton entrantListButton = view.findViewById(R.id.button_entrant_list);
        MaterialButton invitePrivateEntrantsButton = view.findViewById(R.id.button_invite_private_entrants);
        MaterialButton assignCoOrganizerButton = view.findViewById(R.id.button_assign_coorganizer);
        MaterialButton editButton = view.findViewById(R.id.button_edit_event);
        MaterialButton mapButton = view.findViewById(R.id.button_show_mapped_location);
        MaterialButton deleteEventButton = view.findViewById(R.id.button_delete_event);

        titleView.setText("Manage Event");
        subtitleView.setText("Review event details, manage the entrant pool, and handle invitations.");
        backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        deleteEventButton.setOnClickListener(v -> showDeleteEventConfirmDialog(deleteEventButton));

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
            nameView.setText(EventUiFormatter.getTitle(event));
            summaryView.setText(buildSummary(event));
            statusView.setText(buildStatusSummary(event));
            invitePrivateEntrantsButton.setVisibility(event.isPrivateEvent() ? View.VISIBLE : View.GONE);
            mapButton.setVisibility((event.isGeolocationRequired() || event.hasGeofence())
                    ? View.VISIBLE
                    : View.GONE);
            entrantListButton.setText(event.isDrawHappened()
                    ? "Manage Entrants and Replacements"
                    : "Manage Entrants and Run Draw");
            updateDeleteEventVisibility(deleteEventButton);

            updateButtonVisibilities();

            if (openEventPostingOnLoad) {
                openEventPostingOnLoad = false;
                view.post(this::openEventPosting);
            }
        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Failed to load event: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });

        profileService.loadCurrentProfile(requireContext(), profile -> {
            if (!isAdded()) return;
            currentUserProfile = profile;
            currentUserUid = profile != null ? profile.getUid() : null;
            updateButtonVisibilities();
        }, error -> {
            currentUserUid = null;
            currentUserProfile = null;
        });

        viewPageButton.setOnClickListener(v -> {
            openEventPosting();
        });

        entrantListButton.setOnClickListener(v -> {
            if (eventId != null) {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.organizerViewEntrantsFragment, EventNavArgs.forEventId(eventId));
            }
        });

        invitePrivateEntrantsButton.setOnClickListener(v -> {
            if (eventId == null) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.privateEventInviteFragment, EventNavArgs.forEventId(eventId));
        });

        assignCoOrganizerButton.setOnClickListener(v -> {
            if (eventId == null) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.assignCoOrganizerFragment, EventNavArgs.forEventId(eventId));
        });

        mapButton.setOnClickListener(v -> {
            if (eventId == null) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_manageEventFragment_to_entrantMapFragment, EventNavArgs.forEventId(eventId));
        });

        editButton.setOnClickListener(v -> {
            if (eventId != null) {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.editEventFragment, EventNavArgs.forEventId(eventId));
            }
        });

        updateButtonVisibilities();
    }

    private void updateButtonVisibilities() {
        View view = getView();
        if (view == null) return;

        MaterialButton deleteEventButton = view.findViewById(R.id.button_delete_event);
        MaterialButton editButton = view.findViewById(R.id.button_edit_event);
        MaterialButton invitePrivateEntrantsButton = view.findViewById(R.id.button_invite_private_entrants);
        MaterialButton assignCoOrganizerButton = view.findViewById(R.id.button_assign_coorganizer);

        boolean isOrganizer = loadedEvent != null && currentUserUid != null 
                && currentUserUid.equals(loadedEvent.getOrganizerUid());
        boolean isAdmin = currentUserProfile != null && currentUserProfile.isAdmin();
        boolean hasControl = isOrganizer || isAdmin;

        if (deleteEventButton != null) {
            deleteEventButton.setVisibility(hasControl ? View.VISIBLE : View.GONE);
        }
        if (editButton != null) {
            editButton.setVisibility(hasControl ? View.VISIBLE : View.GONE);
        }
        if (assignCoOrganizerButton != null) {
            assignCoOrganizerButton.setVisibility(hasControl ? View.VISIBLE : View.GONE);
        }
        // Invite button should only show for private events AND if user has control
        if (invitePrivateEntrantsButton != null) {
            invitePrivateEntrantsButton.setVisibility(
                    (loadedEvent != null && loadedEvent.isPrivateEvent() && hasControl) 
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void openEventPosting() {
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(),
                    "Missing event id.", Toast.LENGTH_SHORT).show();
            return;
        }
        EventDetailsBottomSheet.newInstance(eventId, true)
                .show(getParentFragmentManager(), "event_details");
    }

    @NonNull
    private String buildSummary(@NonNull Event event) {
        List<String> parts = new ArrayList<>();
        parts.add(EventUiFormatter.getScheduleLabel(event));
        parts.add(EventUiFormatter.getLocationDetailLabel(event));
        if (event.getSampleSize() > 0) {
            parts.add("Target draw " + event.getSampleSize());
        }
        return String.join(" | ", parts);
    }

    @NonNull
    private String buildStatusSummary(@NonNull Event event) {
        List<String> parts = new ArrayList<>();
        parts.add(event.isPrivateEvent() ? "Private event" : "Public event");
        parts.add(event.isDrawHappened() ? "Draw complete" : "Draw pending");
        parts.add(event.isGeolocationRequired() ? "Geolocation required" : "Geolocation optional");
        String geofenceSummary = EventUiFormatter.getGeofenceSummary(event);
        if (geofenceSummary != null) {
            parts.add(geofenceSummary);
        }
        return String.join(" | ", parts);
    }

    private void updateDeleteEventVisibility(@NonNull MaterialButton deleteEventButton) {
        // Handled by updateButtonVisibilities
    }

    private void showDeleteEventConfirmDialog(@NonNull MaterialButton deleteEventButton) {
        if (loadedEvent == null || eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Event not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Cancel and delete event")
                .setMessage("This will notify entrants, write an admin audit log, and permanently remove the event from organizer and entrant views.")
                .setPositiveButton("Delete Event", (dialog, which) -> deleteEventButton.post(
                        () -> deleteCurrentEvent(deleteEventButton)
                ))
                .setNegativeButton("Keep Event", null)
                .show();
    }

    private void deleteCurrentEvent(@NonNull MaterialButton deleteEventButton) {
        if (loadedEvent == null || eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Event not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isAdmin = currentUserProfile != null && currentUserProfile.isAdmin();
        if (!isAdmin && (currentUserUid == null || !currentUserUid.equals(loadedEvent.getOrganizerUid()))) {
            Toast.makeText(requireContext(),
                    "Only the event organizer or an admin can delete this event.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        deleteEventButton.setEnabled(false);
        Event eventToDelete = loadedEvent;
        waitingListService.getEntriesForEvent(eventId, entries -> {
            if (!isAdded()) {
                return;
            }
            organizerNotificationService.notifyEventCancelled(
                    eventToDelete.getOrganizerUid(),
                    eventToDelete,
                    entries,
                    result -> writeDeletionAuditAndDelete(
                            eventToDelete,
                            entries,
                            result.getRecipientCount(),
                            null,
                            deleteEventButton
                    ),
                    error -> writeDeletionAuditAndDelete(
                            eventToDelete,
                            entries,
                            0,
                            error.getMessage(),
                            deleteEventButton
                    )
            );
        }, error -> {
            if (!isAdded()) return;
            deleteEventButton.setEnabled(true);
            Toast.makeText(requireContext(),
                    "Failed to prepare event deletion: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void writeDeletionAuditAndDelete(
            @NonNull Event event,
            @NonNull List<WaitingListEntry> entries,
            int notifiedRecipients,
            @Nullable String notificationError,
            @NonNull MaterialButton deleteEventButton
    ) {
        organizerNotificationService.logEventAudit(
                event.getOrganizerUid(),
                event,
                "Audit: organizer deleted event",
                buildDeletionAuditMessage(event, entries, notifiedRecipients, notificationError),
                unused -> deleteEventRecord(event, deleteEventButton, notificationError),
                error -> {
                    if (!isAdded()) return;
                    deleteEventButton.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Failed to log event deletion: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
        );
    }

    private void deleteEventRecord(
            @NonNull Event event,
            @NonNull MaterialButton deleteEventButton,
            @Nullable String notificationError
    ) {
        eventService.deleteEvent(event.getEventId(), unused -> {
            if (!isAdded()) return;
            String message = notificationError == null
                    ? "Event deleted."
                    : "Event deleted. Some entrant notifications failed.";
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack(R.id.organizeFragment, false);
        }, error -> {
            if (!isAdded()) return;
            deleteEventButton.setEnabled(true);
            Toast.makeText(requireContext(),
                    "Failed to delete event: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    @NonNull
    private String buildDeletionAuditMessage(
            @NonNull Event event,
            @NonNull List<WaitingListEntry> entries,
            int notifiedRecipients,
            @Nullable String notificationError
    ) {
        int invitedCount = 0;
        int acceptedCount = 0;
        int waitingCount = 0;
        int declinedCount = 0;
        int cancelledCount = 0;
        int notSelectedCount = 0;

        for (WaitingListEntry entry : entries) {
            if (entry == null || entry.getStatus() == null) {
                continue;
            }
            switch (entry.getStatus()) {
                case INVITED:
                    invitedCount++;
                    break;
                case ACCEPTED:
                    acceptedCount++;
                    break;
                case WAITING:
                    waitingCount++;
                    break;
                case DECLINED:
                    declinedCount++;
                    break;
                case CANCELLED:
                    cancelledCount++;
                    break;
                case NOT_SELECTED:
                    notSelectedCount++;
                    break;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Organizer deleted event ");
        builder.append(EventUiFormatter.getTitle(event));
        builder.append(" (eventId=");
        builder.append(event.getEventId());
        builder.append("). ");
        builder.append("Entrants at deletion: invited=");
        builder.append(invitedCount);
        builder.append(", accepted=");
        builder.append(acceptedCount);
        builder.append(", waiting=");
        builder.append(waitingCount);
        builder.append(", declined=");
        builder.append(declinedCount);
        builder.append(", cancelled=");
        builder.append(cancelledCount);
        builder.append(", notSelected=");
        builder.append(notSelectedCount);
        builder.append(". Notification records written for ");
        builder.append(notifiedRecipients);
        builder.append(" recipients.");
        if (notificationError != null && !notificationError.trim().isEmpty()) {
            builder.append(" Notification issue: ");
            builder.append(notificationError);
            builder.append('.');
        }
        return builder.toString();
    }
}
