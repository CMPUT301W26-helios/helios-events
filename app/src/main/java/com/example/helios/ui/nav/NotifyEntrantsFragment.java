package com.example.helios.ui.nav;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.NotificationAudience;
import com.example.helios.service.EventService;
import com.example.helios.service.OrganizerNotificationService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.common.HeliosUi;
import com.example.helios.ui.event.EventUiFormatter;
import com.google.android.material.button.MaterialButton;

public class NotifyEntrantsFragment extends Fragment {
    private EventService eventService;
    private ProfileService profileService;
    private OrganizerNotificationService organizerNotificationService;

    @Nullable
    private String eventId;
    @Nullable
    private Event loadedEvent;

    public NotifyEntrantsFragment() {
        super(R.layout.fragment_notify_entrants);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        profileService = application.getProfileService();
        organizerNotificationService = application.getOrganizerNotificationService();
        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString("arg_event_id");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (eventId == null || eventId.trim().isEmpty()) {
            HeliosUi.toast(this,"Missing event id.");
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        TextView titleView = view.findViewById(R.id.submenu_title);
        TextView subtitleView = view.findViewById(R.id.submenu_subtitle);
        EditText titleInput = view.findViewById(R.id.et_notification_title);
        EditText bodyInput = view.findViewById(R.id.et_notification_body);
        RadioButton rbWaiting = view.findViewById(R.id.rb_audience_waiting);
        RadioButton rbSelected = view.findViewById(R.id.rb_audience_selected);
        RadioButton rbCancelled = view.findViewById(R.id.rb_audience_cancelled);
        MaterialButton sendButton = view.findViewById(R.id.btn_send_notification);
        MaterialButton cancelButton = view.findViewById(R.id.submenu_back_button);

        titleView.setText("Notify Entrants");
        subtitleView.setText("Send an organizer message to waiting, selected, or cancelled entrants.");

        cancelButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        sendButton.setOnClickListener(v -> {
            String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
            String body = bodyInput.getText() == null ? "" : bodyInput.getText().toString().trim();
            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(body)) {
                HeliosUi.toast(this,"Title and message are required.");
                return;
            }

            NotificationAudience audience;
            if (rbWaiting.isChecked()) {
                audience = NotificationAudience.WAITING;
            } else if (rbSelected.isChecked()) {
                audience = NotificationAudience.SELECTED;
            } else if (rbCancelled.isChecked()) {
                audience = NotificationAudience.CANCELLED;
            } else {
                HeliosUi.toast(this,"Choose an audience.");
                return;
            }

            sendButton.setEnabled(false);
            sendNotifications(audience, title, body, sendButton);
        });

        loadEventAndAuthorize();
    }

    private void loadEventAndAuthorize() {
        profileService.ensureSignedIn(firebaseUser -> eventService.getEventById(eventId, event -> {
            if (!isAdded()) return;
            if (event == null) {
                HeliosUi.toast(this,"Event not found.");
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            loadedEvent = event;
            String eventLabel = EventUiFormatter.getTitle(event);
            View view = getView();
            if (view != null) {
                TextView subtitleView = view.findViewById(R.id.submenu_subtitle);
                subtitleView.setText("Event: " + eventLabel);
            }
            if (!firebaseUser.getUid().equals(event.getOrganizerUid())) {
                HeliosUi.toast(this,"Only the organizer can send entrant notifications.");
                NavHostFragment.findNavController(this).navigateUp();
            }
        }, e -> HeliosUi.toast(this,"Failed to load event: " + e.getMessage())), e -> HeliosUi.toast(this,"Auth failed: " + e.getMessage()));
    }

    private void sendNotifications(
            @NonNull NotificationAudience audience,
            @NonNull String title,
            @NonNull String message,
            @NonNull MaterialButton sendButton
    ) {
        if (loadedEvent == null) {
            sendButton.setEnabled(true);
            HeliosUi.toast(this,"Event not loaded.");
            return;
        }

        String eventTitle = loadedEvent.getTitle() == null || loadedEvent.getTitle().trim().isEmpty()
                ? "Unknown Event"
                : loadedEvent.getTitle().trim();
        String messageWithEventContext = "Event: " + eventTitle + "\n\n" + message;

        profileService.ensureSignedIn(firebaseUser -> organizerNotificationService.sendToAudience(
                firebaseUser.getUid(),
                eventId,
                audience,
                title,
                messageWithEventContext,
                result -> {
                    if (!isAdded()) return;
                    sendButton.setEnabled(true);
                    HeliosUi.toast(this,"Sent " + result.getRecipientCount() + " notifications.");
                },
                e -> {
                    if (!isAdded()) return;
                    sendButton.setEnabled(true);
                    HeliosUi.toast(this,"Send failed: " + e.getMessage());
                }
        ), e -> {
            if (!isAdded()) return;
            sendButton.setEnabled(true);
            HeliosUi.toast(this,"Auth failed: " + e.getMessage());
        });
    }

}
