package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.data.NotificationRepository;
import com.example.helios.model.Event;
import com.example.helios.model.EventComment;
import com.example.helios.model.NotificationRecord;
import com.example.helios.model.UserProfile;
import com.example.helios.service.AdminGeolocationTestService;
import com.example.helios.service.AdminNotificationTestService;
import com.example.helios.service.CommentService;
import com.example.helios.service.EventService;
import com.example.helios.service.ImageService;
import com.example.helios.service.NotificationSendResult;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.AdminCommentAdapter;
import com.example.helios.ui.AdminEventAdapter;
import com.example.helios.ui.AdminImageAdapter;
import com.example.helios.ui.AdminNotificationAdapter;
import com.example.helios.ui.UserAdapter;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.HeliosUi;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fragment for administrative tasks: managing all events, users, uploaded images, and
 * the notification audit log.
 */
public class AdminFragment extends Fragment {

    private EventService eventService;
    private ProfileService profileService;
    private ImageService imageService;
    private NotificationRepository notificationRepository;
    private AdminGeolocationTestService geolocationTestService;
    private AdminNotificationTestService notificationTestService;
    private CommentService commentService;

    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> displayedEvents = new ArrayList<>();
    private final List<UserProfile> allUsers = new ArrayList<>();
    private final List<UserProfile> displayedUsers = new ArrayList<>();
    private final List<Event> allImages = new ArrayList<>();
    private final List<Event> displayedImages = new ArrayList<>();
    private final List<EventComment> displayedComments = new ArrayList<>();
    private final List<NotificationRecord> displayedNotifications = new ArrayList<>();
    private final Set<String> organizerUids = new HashSet<>();
    private final Map<String, String> eventTitlesById = new HashMap<>();

    private RecyclerView rvEvents;
    private RecyclerView rvUsers;
    private RecyclerView rvImages;
    private RecyclerView rvComments;
    private RecyclerView rvNotifications;

    private AdminEventAdapter eventAdapter;
    private UserAdapter userAdapter;
    private AdminImageAdapter imageAdapter;
    private AdminCommentAdapter commentAdapter;
    private AdminNotificationAdapter notificationAdapter;

    private TextView tvNoImages;
    private TextView tvNoComments;
    private TextView tvNoNotifications;
    private TextView tvGeolocationLabStatus;
    private TextView tvGeolocationLabToggleState;
    private TextView tvNotificationLabStatus;
    private TextView tvNotificationLabToggleState;

    private TextView btnTabEvents;
    private TextView btnTabUsers;
    private TextView btnTabImages;
    private TextView btnTabComments;
    private TextView btnTabGeo;
    private TextView btnTabNotifications;

    private LinearLayout layoutGeoTab;
    private LinearLayout layoutNotificationsTab;
    private LinearLayout layoutGeolocationLabToggle;
    private LinearLayout layoutGeolocationLabContent;
    private LinearLayout layoutNotificationLabToggle;
    private LinearLayout layoutNotificationLabContent;
    private MaterialButton btnGeolocationLabSeed;
    private MaterialButton btnGeolocationLabMenu;
    private MaterialButton btnNotificationLabSeed;
    private MaterialButton btnNotificationLabMenu;
    private boolean geolocationLabExpanded;
    private boolean notificationLabExpanded;

    @Nullable
    private AdminGeolocationTestService.SandboxState geolocationLabState;

    @Nullable
    private AdminNotificationTestService.SandboxState notificationLabState;

    public AdminFragment() {
        super(R.layout.fragment_admin);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvHeaderTitle = view.findViewById(R.id.tvScreenTitle);
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText("Admin Controls");
        }

        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        profileService = application.getProfileService();
        imageService = application.getImageService();
        notificationRepository = application.getNotificationRepository();
        geolocationTestService = application.getAdminGeolocationTestService();
        notificationTestService = application.getAdminNotificationTestService();
        commentService = application.getCommentService();

        btnTabEvents = view.findViewById(R.id.btn_tab_events);
        btnTabUsers = view.findViewById(R.id.btn_tab_users);
        btnTabImages = view.findViewById(R.id.btn_tab_images);
        btnTabComments = view.findViewById(R.id.btn_tab_comments);
        btnTabGeo = view.findViewById(R.id.btn_tab_geo);
        btnTabNotifications = view.findViewById(R.id.btn_tab_notifications);

        rvEvents = view.findViewById(R.id.rv_events);
        rvUsers = view.findViewById(R.id.rv_users);
        rvImages = view.findViewById(R.id.rv_images);
        rvComments = view.findViewById(R.id.rv_comments);
        rvNotifications = view.findViewById(R.id.rv_notifications);

        tvNoImages = view.findViewById(R.id.tv_no_images);
        tvNoComments = view.findViewById(R.id.tv_no_comments);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications);
        tvGeolocationLabStatus = view.findViewById(R.id.tv_geolocation_lab_status);
        tvGeolocationLabToggleState = view.findViewById(R.id.tv_geolocation_lab_toggle_state);
        tvNotificationLabStatus = view.findViewById(R.id.tv_notification_lab_status);
        tvNotificationLabToggleState = view.findViewById(R.id.tv_notification_lab_toggle_state);

        layoutGeoTab = view.findViewById(R.id.layout_geo_tab);
        layoutNotificationsTab = view.findViewById(R.id.layout_notifications_tab);
        layoutGeolocationLabToggle = view.findViewById(R.id.layout_geolocation_lab_toggle);
        layoutGeolocationLabContent = view.findViewById(R.id.layout_geolocation_lab_content);
        layoutNotificationLabToggle = view.findViewById(R.id.layout_notification_lab_toggle);
        layoutNotificationLabContent = view.findViewById(R.id.layout_notification_lab_content);
        btnGeolocationLabSeed = view.findViewById(R.id.btn_geolocation_lab_seed);
        btnGeolocationLabMenu = view.findViewById(R.id.btn_geolocation_lab_menu);
        btnNotificationLabSeed = view.findViewById(R.id.btn_notification_lab_seed);
        btnNotificationLabMenu = view.findViewById(R.id.btn_notification_lab_menu);

        bindEventList();
        bindUserList();
        bindImageList();
        bindCommentList();
        bindNotificationList();
        bindTabs();
        bindGeolocationLab();
        bindNotificationLab();

        showTab(Tab.EVENTS);
        loadEvents();
        loadUsers();
        loadImages();
        loadComments();
        loadNotifications();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
        loadUsers();
        loadImages();
        loadComments();
        loadNotifications();
    }

    private enum Tab { EVENTS, USERS, IMAGES, COMMENTS, GEO, NOTIFICATIONS }

    private void bindEventList() {
        eventAdapter = new AdminEventAdapter(
                displayedEvents,
                event -> {
                    String eventId = event.getEventId();
                    if (!isNonEmpty(eventId)) {
                        toast("Event is missing an ID.");
                        return;
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Delete Event")
                            .setMessage("Are you sure you want to delete \"" + event.getTitle() + "\"? This cannot be undone.")
                            .setPositiveButton("Delete", (dialog, which) -> deleteEvent(event))
                            .setNegativeButton("Cancel", null)
                            .show();
                },
                this::showOrganizerInfo
        );
        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);
    }

    private void bindUserList() {
        userAdapter = new UserAdapter(
                displayedUsers,
                organizerUids,
                user -> {
                    String currentUid = HeliosApplication.from(requireContext())
                            .getAuthDeviceService()
                            .getCurrentUid();
                    if (user.getUid() != null && user.getUid().equals(currentUid)) {
                        toast("You cannot delete your own account.");
                        return;
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Delete User")
                            .setMessage("Are you sure you want to delete \"" + user.getName() + "\"? This cannot be undone.")
                            .setPositiveButton("Delete", (dialog, which) -> deleteUser(user))
                            .setNegativeButton("Cancel", null)
                            .show();
                },
                this::showUserEvents
        );
        rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUsers.setAdapter(userAdapter);
    }

    private void bindImageList() {
        imageAdapter = new AdminImageAdapter(displayedImages, event ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Image")
                        .setMessage("Permanently remove the poster from \"" +
                                nonEmptyOr(event.getTitle(), "this event") + "\"? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> deleteImage(event))
                        .setNegativeButton("Cancel", null)
                        .show()
        );
        rvImages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvImages.setAdapter(imageAdapter);
    }

    private void bindNotificationList() {
        notificationAdapter = new AdminNotificationAdapter(
                displayedNotifications,
                eventTitlesById,
                this::confirmDeleteNotification
        );
        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotifications.setAdapter(notificationAdapter);
        updateNoNotificationsState();
    }

    private void bindCommentList() {
        commentAdapter = new AdminCommentAdapter(
                displayedComments,
                eventTitlesById,
                this::confirmDeleteComment
        );
        rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvComments.setAdapter(commentAdapter);
        updateNoCommentsState();
    }

    private void bindTabs() {
        btnTabEvents.setOnClickListener(v -> showTab(Tab.EVENTS));
        btnTabUsers.setOnClickListener(v -> showTab(Tab.USERS));
        btnTabImages.setOnClickListener(v -> showTab(Tab.IMAGES));
        btnTabComments.setOnClickListener(v -> showTab(Tab.COMMENTS));
        btnTabGeo.setOnClickListener(v -> showTab(Tab.GEO));
        btnTabNotifications.setOnClickListener(v -> showTab(Tab.NOTIFICATIONS));
    }

    private void bindNotificationLab() {
        if (layoutNotificationLabToggle != null) {
            layoutNotificationLabToggle.setOnClickListener(v ->
                    setNotificationLabExpanded(!notificationLabExpanded));
        }
        btnNotificationLabSeed.setOnClickListener(v -> refreshNotificationSandbox());
        btnNotificationLabMenu.setOnClickListener(this::showNotificationLabMenu);
        setNotificationLabExpanded(false);
        updateNotificationLabStatus("Sandbox not prepared yet.");
    }

    private void bindGeolocationLab() {
        if (layoutGeolocationLabToggle != null) {
            layoutGeolocationLabToggle.setOnClickListener(v ->
                    setGeolocationLabExpanded(!geolocationLabExpanded));
        }
        if (btnGeolocationLabSeed != null) {
            btnGeolocationLabSeed.setOnClickListener(v -> refreshGeolocationSandbox());
        }
        if (btnGeolocationLabMenu != null) {
            btnGeolocationLabMenu.setOnClickListener(this::showGeolocationLabMenu);
        }
        setGeolocationLabExpanded(false);
        updateGeolocationLabStatus("Sandbox not prepared yet.");
    }

    private void setNotificationLabExpanded(boolean expanded) {
        notificationLabExpanded = expanded;
        if (layoutNotificationLabContent != null) {
            layoutNotificationLabContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (tvNotificationLabToggleState != null) {
            tvNotificationLabToggleState.setText(expanded ? "Hide" : "Show");
        }
    }

    private void setGeolocationLabExpanded(boolean expanded) {
        geolocationLabExpanded = expanded;
        if (layoutGeolocationLabContent != null) {
            layoutGeolocationLabContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (tvGeolocationLabToggleState != null) {
            tvGeolocationLabToggleState.setText(expanded ? "Hide" : "Show");
        }
    }

    private void showTab(@NonNull Tab tab) {
        rvEvents.setVisibility(tab == Tab.EVENTS ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(tab == Tab.USERS ? View.VISIBLE : View.GONE);
        rvImages.setVisibility(tab == Tab.IMAGES ? View.VISIBLE : View.GONE);
        rvComments.setVisibility(tab == Tab.COMMENTS ? View.VISIBLE : View.GONE);
        layoutGeoTab.setVisibility(tab == Tab.GEO ? View.VISIBLE : View.GONE);
        layoutNotificationsTab.setVisibility(tab == Tab.NOTIFICATIONS ? View.VISIBLE : View.GONE);

        updateNoImagesState(tab == Tab.IMAGES);
        updateNoCommentsState(tab == Tab.COMMENTS);
        updateNoNotificationsState(tab == Tab.NOTIFICATIONS);

        btnTabEvents.setSelected(tab == Tab.EVENTS);
        btnTabUsers.setSelected(tab == Tab.USERS);
        btnTabImages.setSelected(tab == Tab.IMAGES);
        btnTabComments.setSelected(tab == Tab.COMMENTS);
        btnTabGeo.setSelected(tab == Tab.GEO);
        btnTabNotifications.setSelected(tab == Tab.NOTIFICATIONS);

        switch (tab) {
            case EVENTS:
                loadEvents();
                break;
            case USERS:
                loadUsers();
                break;
            case IMAGES:
                loadImages();
                break;
            case COMMENTS:
                loadComments();
                break;
            case GEO:
                break;
            case NOTIFICATIONS:
                loadNotifications();
                break;
        }
    }

    private void loadEvents() {
        eventService.getAllEvents(events -> {
            if (!isAdded()) {
                return;
            }

            allEvents.clear();
            allEvents.addAll(events);
            displayedEvents.clear();
            displayedEvents.addAll(allEvents);
            rebuildEventMetadata();
            eventAdapter.replaceEvents(displayedEvents);
            userAdapter.replaceUsers(displayedUsers, organizerUids);
            commentAdapter.replaceEventTitles(eventTitlesById);
            notificationAdapter.replaceEventTitles(eventTitlesById);
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load events: " + error.getMessage());
        });
    }

    private void loadUsers() {
        profileService.getAllProfiles(users -> {
            if (!isAdded()) {
                return;
            }
            allUsers.clear();
            allUsers.addAll(users);
            displayedUsers.clear();
            displayedUsers.addAll(allUsers);
            userAdapter.replaceUsers(displayedUsers, organizerUids);
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load users: " + error.getMessage());
        });
    }

    private void loadImages() {
        imageService.getAllImages(events -> {
            if (!isAdded()) {
                return;
            }
            allImages.clear();
            allImages.addAll(events);
            displayedImages.clear();
            displayedImages.addAll(allImages);
            imageAdapter.replaceEvents(displayedImages);
            updateNoImagesState();
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load images: " + error.getMessage());
        });
    }

    private void loadComments() {
        commentService.getAllCommentsForAdmin(comments -> {
            if (!isAdded()) {
                return;
            }
            displayedComments.clear();
            displayedComments.addAll(comments);
            commentAdapter.replaceComments(displayedComments);
            updateNoCommentsState();
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load comments: " + error.getMessage());
        });
    }

    private void loadNotifications() {
        notificationRepository.getAllNotifications(records -> {
            if (!isAdded()) {
                return;
            }
            displayedNotifications.clear();
            displayedNotifications.addAll(records);
            notificationAdapter.replaceNotifications(displayedNotifications);
            updateNoNotificationsState();
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load notifications: " + error.getMessage());
        });
    }

    private void deleteEvent(@NonNull Event event) {
        eventService.deleteEvent(event.getEventId(), unused -> {
            if (!isAdded()) {
                return;
            }
            allEvents.remove(event);
            displayedEvents.remove(event);
            rebuildEventMetadata();
            eventAdapter.replaceEvents(displayedEvents);
            userAdapter.replaceUsers(displayedUsers, organizerUids);
            commentAdapter.replaceEventTitles(eventTitlesById);
            notificationAdapter.replaceEventTitles(eventTitlesById);
            loadComments();
            toast("Event deleted.");
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Delete failed: " + error.getMessage());
        });
    }

    private void deleteUser(@NonNull UserProfile user) {
        profileService.deleteProfile(user.getUid(), unused -> {
            if (!isAdded()) {
                return;
            }
            allUsers.remove(user);
            displayedUsers.remove(user);

            String uid = user.getUid();
            allEvents.removeIf(event -> uid.equals(event.getOrganizerUid()));
            displayedEvents.removeIf(event -> uid.equals(event.getOrganizerUid()));
            rebuildEventMetadata();
            userAdapter.replaceUsers(displayedUsers, organizerUids);
            eventAdapter.replaceEvents(displayedEvents);
            commentAdapter.replaceEventTitles(eventTitlesById);
            notificationAdapter.replaceEventTitles(eventTitlesById);

            toast("User deleted.");
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Delete failed: " + error.getMessage());
        });
    }

    private void confirmDeleteComment(@NonNull EventComment comment) {
        String eventTitle = eventTitlesById.get(comment.getEventId());
        String message = "Remove this comment from "
                + "\"" + nonEmptyOr(eventTitle, "the event") + "\"?"
                + "\n\n" + nonEmptyOr(comment.getBody(), "(empty comment)");
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Comment")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> deleteComment(comment))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteComment(@NonNull EventComment comment) {
        String eventId = comment.getEventId();
        if (!isNonEmpty(eventId)) {
            toast("Comment is missing an event ID.");
            return;
        }
        commentService.deleteComment(requireContext(), eventId, comment, unused -> {
            if (!isAdded()) {
                return;
            }
            displayedComments.remove(comment);
            commentAdapter.replaceComments(displayedComments);
            updateNoCommentsState();
            toast("Comment deleted.");
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Delete failed: " + error.getMessage());
        });
    }

    private void deleteImage(@NonNull Event event) {
        imageService.deleteImage(event, unused -> {
            if (!isAdded()) {
                return;
            }
            allImages.remove(event);
            displayedImages.remove(event);
            imageAdapter.replaceEvents(displayedImages);
            updateNoImagesState();
            toast("Image deleted.");
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Delete failed: " + error.getMessage());
        });
    }

    private void confirmDeleteNotification(@NonNull NotificationRecord record) {
        if (!isNonEmpty(record.getNotificationId())) {
            toast("Notification is missing an ID.");
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Notification Log Entry")
                .setMessage("Remove \"" + nonEmptyOr(record.getTitle(), "this notification") + "\" from the audit log?")
                .setPositiveButton("Delete", (dialog, which) -> deleteNotification(record))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteNotification(@NonNull NotificationRecord record) {
        notificationRepository.deleteNotification(record.getNotificationId(), unused -> {
            if (!isAdded()) {
                return;
            }
            displayedNotifications.remove(record);
            notificationAdapter.replaceNotifications(displayedNotifications);
            updateNoNotificationsState();
            toast("Notification log entry deleted.");
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Delete failed: " + error.getMessage());
        });
    }

    private void refreshNotificationSandbox() {
        setNotificationLabBusy(true);
        notificationTestService.createOrRefreshSandbox(state -> {
            if (!isAdded()) {
                return;
            }
            setNotificationLabBusy(false);
            notificationLabState = state;
            updateNotificationLabStatus("Sandbox refreshed.");
            loadEvents();
            loadNotifications();
            toast("Notification sandbox refreshed.");
        }, error -> handleNotificationLabFailure("Sandbox refresh failed", error));
    }

    private void refreshGeolocationSandbox() {
        setGeolocationLabBusy(true);
        geolocationTestService.createOrRefreshSandbox(state -> {
            if (!isAdded()) {
                return;
            }
            setGeolocationLabBusy(false);
            geolocationLabState = state;
            updateGeolocationLabStatus("Sandbox refreshed.");
            loadEvents();
            toast("Geolocation sandbox refreshed.");
        }, error -> handleGeolocationLabFailure("Sandbox refresh failed", error));
    }

    private void refreshRegionalGeolocationSandbox() {
        setGeolocationLabBusy(true);
        geolocationTestService.createRegionalSpreadSandbox(state -> {
            if (!isAdded()) {
                return;
            }
            setGeolocationLabBusy(false);
            geolocationLabState = state;
            updateGeolocationLabStatus("Regional sandbox refreshed.");
            loadEvents();
            toast("Regional geolocation sandbox refreshed.");
        }, error -> handleGeolocationLabFailure("Regional sandbox refresh failed", error));
    }

    private void showNotificationLabMenu(@NonNull View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.inflate(R.menu.admin_notification_test_menu);
        popupMenu.setOnMenuItemClickListener(item -> {
            handleNotificationLabMenuItem(item.getItemId());
            return true;
        });
        popupMenu.show();
    }

    private void showGeolocationLabMenu(@NonNull View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.inflate(R.menu.admin_geolocation_test_menu);
        popupMenu.setOnMenuItemClickListener(item -> {
            handleGeolocationLabMenuItem(item.getItemId());
            return true;
        });
        popupMenu.show();
    }

    private void handleNotificationLabMenuItem(int itemId) {
        if (itemId == R.id.menu_refresh_notification_sandbox) {
            refreshNotificationSandbox();
            return;
        }
        if (itemId == R.id.menu_test_waiting_broadcast) {
            sendWaitingBroadcastTest();
            return;
        }
        if (itemId == R.id.menu_test_selected_broadcast) {
            sendSelectedBroadcastTest();
            return;
        }
        if (itemId == R.id.menu_test_cancelled_broadcast) {
            sendCancelledBroadcastTest();
            return;
        }
        if (itemId == R.id.menu_test_draw_selected) {
            runDrawSelectedTest();
            return;
        }
        if (itemId == R.id.menu_test_draw_not_selected) {
            runDrawNotSelectedTest();
            return;
        }
        if (itemId == R.id.menu_test_private_invite) {
            sendPrivateInviteTest();
            return;
        }
        if (itemId == R.id.menu_test_coorganizer_invite) {
            sendCoOrganizerInviteTest();
        }
    }

    private void handleGeolocationLabMenuItem(int itemId) {
        if (itemId == R.id.menu_refresh_geolocation_sandbox) {
            refreshGeolocationSandbox();
            return;
        }
        if (itemId == R.id.menu_refresh_geolocation_regional_sandbox) {
            refreshRegionalGeolocationSandbox();
            return;
        }
        if (itemId == R.id.menu_open_geolocation_entrant_map) {
            openGeolocationSandboxMap();
        }
    }

    private void sendWaitingBroadcastTest() {
        setNotificationLabBusy(true);
        notificationTestService.sendWaitingBroadcast(result ->
                        handleNotificationSendSuccess("Waiting-list broadcast", result),
                error -> handleNotificationLabFailure("Waiting-list broadcast failed", error)
        );
    }

    private void sendSelectedBroadcastTest() {
        setNotificationLabBusy(true);
        notificationTestService.sendSelectedBroadcastToSelf(result ->
                        handleNotificationSendSuccess("Selected-entrant broadcast", result),
                error -> handleNotificationLabFailure("Selected-entrant broadcast failed", error)
        );
    }

    private void sendCancelledBroadcastTest() {
        setNotificationLabBusy(true);
        notificationTestService.sendCancelledBroadcastToSelf(result ->
                        handleNotificationSendSuccess("Cancelled-entrant broadcast", result),
                error -> handleNotificationLabFailure("Cancelled-entrant broadcast failed", error)
        );
    }

    private void runDrawSelectedTest() {
        setNotificationLabBusy(true);
        notificationTestService.simulateDrawWithAdminSelected(unused ->
                        handleNotificationVoidSuccess("Deterministic draw sent a selected result to this device."),
                error -> handleNotificationLabFailure("Deterministic draw failed", error)
        );
    }

    private void runDrawNotSelectedTest() {
        setNotificationLabBusy(true);
        notificationTestService.simulateDrawWithAdminNotSelected(unused ->
                        handleNotificationVoidSuccess("Deterministic draw sent a not-selected result to this device."),
                error -> handleNotificationLabFailure("Deterministic draw failed", error)
        );
    }

    private void sendPrivateInviteTest() {
        setNotificationLabBusy(true);
        notificationTestService.sendPrivateInviteToSelf(result ->
                        handleNotificationSendSuccess("Private event invite", result),
                error -> handleNotificationLabFailure("Private event invite failed", error)
        );
    }

    private void sendCoOrganizerInviteTest() {
        setNotificationLabBusy(true);
        notificationTestService.sendCoOrganizerInviteToSelf(result ->
                        handleNotificationSendSuccess("Co-organizer invite", result),
                error -> handleNotificationLabFailure("Co-organizer invite failed", error)
        );
    }

    private void openGeolocationSandboxMap() {
        if (!isAdded()) {
            return;
        }
        if (geolocationLabState == null || geolocationLabState.getEvent().getEventId() == null) {
            toast("Refresh the geolocation sandbox first.");
            return;
        }
        updateGeolocationLabStatus("Opened entrant map.");
        NavHostFragment.findNavController(this).navigate(
                R.id.entrantMapFragment,
                EventNavArgs.forEventId(geolocationLabState.getEvent().getEventId())
        );
    }

    private void handleNotificationSendSuccess(
            @NonNull String actionLabel,
            @NonNull NotificationSendResult result
    ) {
        if (!isAdded()) {
            return;
        }
        setNotificationLabBusy(false);
        String statusLine = actionLabel + " complete. Records written: " + result.getRecipientCount() + ".";
        updateNotificationLabStatus(statusLine);
        loadEvents();
        loadNotifications();

        if (result.getRecipientCount() > 0) {
            toast(actionLabel + " wrote " + result.getRecipientCount() + " notification records.");
        } else {
            toast(actionLabel + " completed with 0 recipients.");
        }
    }

    private void handleNotificationVoidSuccess(@NonNull String statusLine) {
        if (!isAdded()) {
            return;
        }
        setNotificationLabBusy(false);
        updateNotificationLabStatus(statusLine);
        loadEvents();
        loadNotifications();
        toast(statusLine);
    }

    private void handleGeolocationLabFailure(@NonNull String prefix, @NonNull Exception error) {
        if (!isAdded()) {
            return;
        }
        setGeolocationLabBusy(false);
        updateGeolocationLabStatus(prefix + ".");
        toast(prefix + ": " + error.getMessage());
    }

    private void handleNotificationLabFailure(@NonNull String prefix, @NonNull Exception error) {
        if (!isAdded()) {
            return;
        }
        setNotificationLabBusy(false);
        updateNotificationLabStatus(prefix + ".");
        toast(prefix + ": " + error.getMessage());
    }

    private void setNotificationLabBusy(boolean busy) {
        if (btnNotificationLabSeed != null) {
            btnNotificationLabSeed.setEnabled(!busy);
        }
        if (btnNotificationLabMenu != null) {
            btnNotificationLabMenu.setEnabled(!busy);
        }
        if (busy) {
            updateNotificationLabStatus("Running notification test...");
        }
    }

    private void setGeolocationLabBusy(boolean busy) {
        if (btnGeolocationLabSeed != null) {
            btnGeolocationLabSeed.setEnabled(!busy);
        }
        if (btnGeolocationLabMenu != null) {
            btnGeolocationLabMenu.setEnabled(!busy);
        }
        if (busy) {
            updateGeolocationLabStatus("Running geolocation test...");
        }
    }

    private void updateNotificationLabStatus(@NonNull String lastAction) {
        if (tvNotificationLabStatus == null) {
            return;
        }

        if (notificationLabState == null) {
            tvNotificationLabStatus.setText(lastAction);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Event: ").append(nonEmptyOr(notificationLabState.getEvent().getTitle(), "Sandbox")).append('\n');
        builder.append("This device: ").append(notificationLabState.getCurrentUserLabel()).append('\n');
        builder.append("Fake entrants: ").append(notificationLabState.getFakeEntrantCount()).append('\n');
        builder.append("Notifications enabled: ")
                .append(notificationLabState.isNotificationsEnabled() ? "Yes" : "No")
                .append(" | Push ready: ")
                .append(notificationLabState.isPushReady() ? "Yes" : "No")
                .append('\n');
        builder.append("Last action: ").append(lastAction);
        tvNotificationLabStatus.setText(builder.toString());
    }

    private void updateGeolocationLabStatus(@NonNull String lastAction) {
        if (tvGeolocationLabStatus == null) {
            return;
        }

        if (geolocationLabState == null) {
            tvGeolocationLabStatus.setText(lastAction);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Event: ").append(nonEmptyOr(geolocationLabState.getEvent().getTitle(), "Sandbox")).append('\n');
        builder.append("Scenario: ").append(geolocationLabState.getScenarioLabel()).append('\n');
        builder.append("This device: ").append(geolocationLabState.getCurrentUserLabel()).append('\n');
        builder.append("Fake entrants: ").append(geolocationLabState.getFakeEntrantCount())
                .append(" | Map points: ").append(geolocationLabState.getLocationPointCount())
                .append('\n');
        builder.append("Last action: ").append(lastAction);
        tvGeolocationLabStatus.setText(builder.toString());
    }

    private void updateNoNotificationsState() {
        updateNoNotificationsState(layoutNotificationsTab != null && layoutNotificationsTab.getVisibility() == View.VISIBLE);
    }

    private void updateNoImagesState() {
        updateNoImagesState(rvImages != null && rvImages.getVisibility() == View.VISIBLE);
    }

    private void updateNoImagesState(boolean imagesVisible) {
        if (tvNoImages == null) {
            return;
        }
        tvNoImages.setVisibility(imagesVisible && displayedImages.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateNoCommentsState() {
        updateNoCommentsState(rvComments != null && rvComments.getVisibility() == View.VISIBLE);
    }

    private void updateNoCommentsState(boolean commentsVisible) {
        if (tvNoComments == null) {
            return;
        }
        tvNoComments.setVisibility(commentsVisible && displayedComments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateNoNotificationsState(boolean notificationsVisible) {
        if (tvNoNotifications == null) {
            return;
        }
        tvNoNotifications.setVisibility(notificationsVisible && displayedNotifications.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showOrganizerInfo(@NonNull Event event) {
        String organizerUid = event.getOrganizerUid();
        if (!isNonEmpty(organizerUid)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Organizer Info")
                    .setMessage("This event has no organizer on record.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        profileService.getUserProfile(organizerUid, profile -> {
            if (!isAdded()) {
                return;
            }
            String name = profile != null && profile.getName() != null ? profile.getName() : "(not set)";
            String email = profile != null && profile.getEmail() != null ? profile.getEmail() : "(not set)";
            String role = profile != null && profile.getRole() != null ? profile.getRole() : "user";

            new AlertDialog.Builder(requireContext())
                    .setTitle("Organizer - " + nonEmptyOr(event.getTitle(), "Untitled Event"))
                    .setMessage("Name: " + name + "\nEmail: " + email + "\nRole: " + role)
                    .setPositiveButton("OK", null)
                    .show();
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load organizer: " + error.getMessage());
        });
    }

    private void showUserEvents(@NonNull UserProfile user) {
        String uid = user.getUid();
        if (!isNonEmpty(uid)) {
            toast("User is missing a UID.");
            return;
        }

        List<Event> userEvents = new ArrayList<>();
        for (Event event : allEvents) {
            if (uid.equals(event.getOrganizerUid())) {
                userEvents.add(event);
            }
        }

        String displayName = user.getName() != null ? user.getName() : "(no name)";

        if (userEvents.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Events by " + displayName)
                    .setMessage("This user has not created any events.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton(
                            user.isOrganizerAccessRevoked()
                                    ? "Restore organizer access"
                                    : "Restrict organizer access",
                            (dialog, which) -> confirmRevokeOrganizerAccess(user, 0))
                    .show();
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < userEvents.size(); i++) {
            Event event = userEvents.get(i);
            builder.append(i + 1).append(". ").append(nonEmptyOr(event.getTitle(), "Untitled Event"));
            if (isNonEmpty(event.getLocationName())) {
                builder.append("\n").append(event.getLocationName());
            }
            if (i < userEvents.size() - 1) {
                builder.append("\n");
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Events by " + displayName + " (" + userEvents.size() + ")")
                .setMessage(builder.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton(
                        user.isOrganizerAccessRevoked()
                                ? "Restore organizer access"
                                : "Restrict organizer access",
                        (dialog, which) -> confirmRevokeOrganizerAccess(user, userEvents.size()))
                .show();
    }

    private void confirmRevokeOrganizerAccess(@NonNull UserProfile user, int userEventCount) {
        String displayName = user.getName() != null ? user.getName() : "(no name)";
        boolean restoreAccess = user.isOrganizerAccessRevoked();
        new AlertDialog.Builder(requireContext())
                .setTitle((restoreAccess ? "Restore" : "Restrict") + " organizer access?")
                .setMessage(restoreAccess
                        ? displayName + " will regain access to organizer features. Existing events will remain unchanged."
                        : displayName + " will keep their profile and entrant/admin access, but organizer features will be blocked. Existing events (" + userEventCount + ") will remain visible until manually removed.")
                .setPositiveButton(restoreAccess ? "Restore" : "Restrict",
                        (dialog, which) -> revokeOrganizerAccess(user, !restoreAccess))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void revokeOrganizerAccess(@NonNull UserProfile user, boolean revoked) {
        String uid = user.getUid();
        if (!isNonEmpty(uid)) {
            toast("User is missing a UID.");
            return;
        }
        profileService.setOrganizerAccessRevoked(uid, revoked, unused -> {
            if (!isAdded()) return;
            user.setOrganizerAccessRevoked(revoked);
            userAdapter.replaceUsers(displayedUsers, organizerUids);
            toast((revoked ? "Restricted" : "Restored")
                    + " organizer access for "
                    + (user.getName() != null ? user.getName() : uid)
                    + ".");
        }, error -> {
            if (!isAdded()) return;
            toast("Failed to update organizer access: " + error.getMessage());
        });
    }

    private void toast(@NonNull String message) {
        HeliosUi.toast(this, message);
    }

    private boolean isNonEmpty(@Nullable String value) {
        return HeliosText.isNonEmpty(value);
    }

    @NonNull
    private String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        return HeliosText.nonEmptyOr(value, fallback);
    }

    private void rebuildEventMetadata() {
        organizerUids.clear();
        eventTitlesById.clear();
        for (Event event : allEvents) {
            if (isNonEmpty(event.getOrganizerUid())) {
                organizerUids.add(event.getOrganizerUid());
            }
            if (isNonEmpty(event.getEventId()) && isNonEmpty(event.getTitle())) {
                eventTitlesById.put(event.getEventId(), event.getTitle().trim());
            }
        }
    }
}
