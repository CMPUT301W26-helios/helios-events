package com.example.helios.ui.nav;

import android.os.Bundle;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private static final String ARG_ORGANIZER_UID = "arg_organizer_uid";

    private EventService eventService;
    private ProfileService profileService;
    private ImageService imageService;
    private NotificationRepository notificationRepository;
    private AdminGeolocationTestService geolocationTestService;
    private AdminNotificationTestService notificationTestService;
    private CommentService commentService;

    private final List<UserProfile> displayedOrganizers = new ArrayList<>();
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> displayedEvents = new ArrayList<>();
    private final List<UserProfile> allUsers = new ArrayList<>();
    private final List<UserProfile> displayedUsers = new ArrayList<>();
    private final List<Event> displayedImages = new ArrayList<>();
    private final List<EventComment> displayedComments = new ArrayList<>();
    private final List<NotificationRecord> displayedNotifications = new ArrayList<>();
    private final Set<String> organizerUids = new HashSet<>();
    private final Map<String, String> eventTitlesById = new HashMap<>();

    private RecyclerView rvUsers, rvOrganizers, rvEvents, rvImages, rvNotifications, rvComments;
    private TextView tvNoUsers, tvNoOrganizers, tvNoEvents, tvNoImages, tvNoNotifications, tvNoComments;
    private TextView btnTabUsers, btnTabOrganizers, btnTabEvents, btnTabImages, btnTabNotifications, btnTabComments, btnTabGeo;
    private HorizontalScrollView tabContainer;
    private android.widget.ImageView ivScrollLeft, ivScrollRight;

    private AdminEventAdapter eventAdapter;
    private UserAdapter userAdapter;
    private UserAdapter organizerAdapter;
    private AdminImageAdapter imageAdapter;
    private AdminCommentAdapter commentAdapter;
    private AdminNotificationAdapter notificationAdapter;

    private TextView tvGeolocationLabStatus;
    private TextView tvGeolocationLabToggleState;
    private TextView tvNotificationLabStatus;
    private TextView tvNotificationLabToggleState;

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

    private String filteredOrganizerUid;
    private boolean isOrganizerMode;
    private String organizerName;

    public AdminFragment() {
        super(R.layout.fragment_admin);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        profileService = application.getProfileService();
        imageService = application.getImageService();
        notificationRepository = application.getNotificationRepository();
        geolocationTestService = application.getAdminGeolocationTestService();
        notificationTestService = application.getAdminNotificationTestService();
        commentService = application.getCommentService();

        if (getArguments() != null) {
            filteredOrganizerUid = getArguments().getString(ARG_ORGANIZER_UID);
        }
        isOrganizerMode = filteredOrganizerUid != null;

        rvUsers = view.findViewById(R.id.rv_users);
        rvOrganizers = view.findViewById(R.id.rv_organizers);
        rvEvents = view.findViewById(R.id.rv_events);
        rvImages = view.findViewById(R.id.rv_images);
        rvNotifications = view.findViewById(R.id.rv_notifications);
        rvComments = view.findViewById(R.id.rv_comments);

        tvNoUsers = view.findViewById(R.id.tv_no_users);
        tvNoOrganizers = view.findViewById(R.id.tv_no_organizers);
        tvNoEvents = view.findViewById(R.id.tv_no_events);
        tvNoImages = view.findViewById(R.id.tv_no_images);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications);
        tvNoComments = view.findViewById(R.id.tv_no_comments);

        btnTabUsers = view.findViewById(R.id.btn_tab_users);
        btnTabOrganizers = view.findViewById(R.id.btn_tab_organizers);
        btnTabEvents = view.findViewById(R.id.btn_tab_events);
        btnTabImages = view.findViewById(R.id.btn_tab_images);
        btnTabNotifications = view.findViewById(R.id.btn_tab_notifications);
        btnTabComments = view.findViewById(R.id.btn_tab_comments);
        btnTabGeo = view.findViewById(R.id.btn_tab_geo);

        tabContainer = view.findViewById(R.id.admin_tab_scroll);
        ivScrollLeft = view.findViewById(R.id.iv_tab_scroll_left);
        ivScrollRight = view.findViewById(R.id.iv_tab_scroll_right);
        tabContainer.getViewTreeObserver().addOnGlobalLayoutListener(this::updateScrollHints);
        tabContainer.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> updateScrollHints());
        MaterialButton backButton = view.findViewById(R.id.submenu_back_button);
        TextView titleView = view.findViewById(R.id.submenu_title);

        if (isOrganizerMode) {
            titleView.setText("Organizer Audit");
            btnTabUsers.setVisibility(View.GONE);
            btnTabOrganizers.setVisibility(View.GONE);
            btnTabEvents.setVisibility(View.GONE);
            btnTabComments.setVisibility(View.GONE);
            btnTabGeo.setVisibility(View.GONE);
            backButton.setVisibility(View.VISIBLE);
            backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        } else {
            titleView.setText("Admin Controls");
            backButton.setVisibility(View.GONE);
            btnTabUsers.setVisibility(View.VISIBLE);
            btnTabOrganizers.setVisibility(View.VISIBLE);
            btnTabEvents.setVisibility(View.VISIBLE);
            btnTabComments.setVisibility(View.VISIBLE);
            btnTabGeo.setVisibility(View.VISIBLE);
        }

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

        setupUserAdapters();
        setupEventAdapter();
        setupImageAdapter();
        setupNotificationAdapter();
        setupCommentAdapter();

        rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUsers.setAdapter(userAdapter);
        rvOrganizers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrganizers.setAdapter(organizerAdapter);
        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);
        rvImages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvImages.setAdapter(imageAdapter);
        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotifications.setAdapter(notificationAdapter);
        rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvComments.setAdapter(commentAdapter);

        bindTabs();
        bindGeolocationLab();
        bindNotificationLab();

        if (isOrganizerMode) {
            showTab(Tab.NOTIFICATIONS);
        } else {
            showTab(Tab.EVENTS);
        }

        loadEvents();
        loadUsers();
        loadImages();
        loadComments();
        loadNotifications();
    }

    private enum Tab {
        USERS, ORGANIZERS, EVENTS, IMAGES, NOTIFICATIONS, COMMENTS, GEO
    }

    private void setupEventAdapter() {
        eventAdapter = new AdminEventAdapter(
                displayedEvents,
                this::onEventDelete,
                this::showOrganizerInfo,
                this::onEventClick
        );
    }

    private void onEventClick(@NonNull Event event) {
        NavHostFragment.findNavController(this)
                .navigate(R.id.manageEventFragment, EventNavArgs.forEventId(event.getEventId()));
    }

    private void setupUserAdapters() {
        userAdapter = new UserAdapter(
                displayedUsers,
                organizerUids,
                this::onUserDelete,
                this::showUserEvents,
                user -> {}
        );

        organizerAdapter = new UserAdapter(
                displayedOrganizers,
                organizerUids,
                this::onUserDelete,
                this::showUserEvents,
                this::openOrganizerMedia
        );
    }

    private void openOrganizerMedia(@NonNull UserProfile organizer) {
        Bundle args = new Bundle();
        args.putString(ARG_ORGANIZER_UID, organizer.getUid());
        NavHostFragment.findNavController(this)
                .navigate(R.id.adminFragment, args);
    }

    private void setupImageAdapter() {
        imageAdapter = new AdminImageAdapter(displayedImages, image ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete Image")
                        .setMessage("Permanently remove this image? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> deleteImage(image))
                        .setNegativeButton("Cancel", null)
                        .show()
        );
    }

    private void setupNotificationAdapter() {
        notificationAdapter = new AdminNotificationAdapter(
                displayedNotifications,
                eventTitlesById,
                this::confirmDeleteNotification
        );
    }

    private void setupCommentAdapter() {
        commentAdapter = new AdminCommentAdapter(
                displayedComments,
                eventTitlesById,
                this::confirmDeleteComment
        );
    }

    private void updateScrollHints() {
        android.view.View child = tabContainer.getChildAt(0);
        if (child == null) return;
        int scrollX = tabContainer.getScrollX();
        int maxScroll = child.getWidth() - tabContainer.getWidth();
        ivScrollLeft.animate().alpha(scrollX > 4 ? 0.75f : 0f).setDuration(150).start();
        ivScrollRight.animate().alpha(scrollX < maxScroll - 4 ? 0.75f : 0f).setDuration(150).start();
    }

    private void bindTabs() {
        btnTabUsers.setOnClickListener(v -> showTab(Tab.USERS));
        btnTabOrganizers.setOnClickListener(v -> showTab(Tab.ORGANIZERS));
        btnTabEvents.setOnClickListener(v -> showTab(Tab.EVENTS));
        btnTabImages.setOnClickListener(v -> showTab(Tab.IMAGES));
        btnTabNotifications.setOnClickListener(v -> showTab(Tab.NOTIFICATIONS));
        btnTabComments.setOnClickListener(v -> showTab(Tab.COMMENTS));
        btnTabGeo.setOnClickListener(v -> showTab(Tab.GEO));
    }

    private void showTab(@NonNull Tab currentTab) {
        updateTabSelection(btnTabUsers, currentTab == Tab.USERS);
        updateTabSelection(btnTabOrganizers, currentTab == Tab.ORGANIZERS);
        updateTabSelection(btnTabEvents, currentTab == Tab.EVENTS);
        updateTabSelection(btnTabImages, currentTab == Tab.IMAGES);
        updateTabSelection(btnTabNotifications, currentTab == Tab.NOTIFICATIONS);
        updateTabSelection(btnTabComments, currentTab == Tab.COMMENTS);
        updateTabSelection(btnTabGeo, currentTab == Tab.GEO);

        HeliosUi.setVisible(rvUsers, currentTab == Tab.USERS);
        HeliosUi.setVisible(rvOrganizers, currentTab == Tab.ORGANIZERS);
        HeliosUi.setVisible(rvEvents, currentTab == Tab.EVENTS);
        HeliosUi.setVisible(rvImages, currentTab == Tab.IMAGES);
        HeliosUi.setVisible(rvNotifications, currentTab == Tab.NOTIFICATIONS);
        HeliosUi.setVisible(rvComments, currentTab == Tab.COMMENTS);
        HeliosUi.setVisible(layoutGeoTab, currentTab == Tab.GEO);
        HeliosUi.setVisible(layoutNotificationsTab, currentTab == Tab.NOTIFICATIONS);

        HeliosUi.setVisible(tvNoUsers, currentTab == Tab.USERS && displayedUsers.isEmpty());
        HeliosUi.setVisible(tvNoOrganizers, currentTab == Tab.ORGANIZERS && displayedOrganizers.isEmpty());
        HeliosUi.setVisible(tvNoEvents, currentTab == Tab.EVENTS && displayedEvents.isEmpty());
        HeliosUi.setVisible(tvNoImages, currentTab == Tab.IMAGES && displayedImages.isEmpty());
        HeliosUi.setVisible(tvNoNotifications, currentTab == Tab.NOTIFICATIONS && displayedNotifications.isEmpty());
        HeliosUi.setVisible(tvNoComments, currentTab == Tab.COMMENTS && displayedComments.isEmpty());
    }

    private void updateTabSelection(TextView tab, boolean selected) {
        tab.setSelected(selected);
    }

    private void updateEmptyStates() {
        HeliosUi.setVisible(tvNoUsers, rvUsers.getVisibility() == View.VISIBLE && displayedUsers.isEmpty());
        HeliosUi.setVisible(tvNoOrganizers, rvOrganizers.getVisibility() == View.VISIBLE && displayedOrganizers.isEmpty());
        HeliosUi.setVisible(tvNoEvents, rvEvents.getVisibility() == View.VISIBLE && displayedEvents.isEmpty());
        HeliosUi.setVisible(tvNoImages, rvImages.getVisibility() == View.VISIBLE && displayedImages.isEmpty());
        HeliosUi.setVisible(tvNoNotifications, rvNotifications.getVisibility() == View.VISIBLE && displayedNotifications.isEmpty());
        HeliosUi.setVisible(tvNoComments, rvComments.getVisibility() == View.VISIBLE && displayedComments.isEmpty());
    }

    private void loadEvents() {
        eventService.getAllEvents(events -> {
            if (!isAdded()) return;
            allEvents.clear();
            allEvents.addAll(events);
            displayedEvents.clear();
            if (isOrganizerMode) {
                for (Event e : events) {
                    if (filteredOrganizerUid.equals(e.getOrganizerUid())) displayedEvents.add(e);
                }
            } else {
                displayedEvents.addAll(events);
            }
            eventAdapter.replaceEvents(displayedEvents);
            rebuildEventMetadata();
            userAdapter.replaceUsers(displayedUsers, organizerUids);
            organizerAdapter.replaceUsers(displayedOrganizers, organizerUids);
            commentAdapter.replaceEventTitles(eventTitlesById);
            notificationAdapter.replaceEventTitles(eventTitlesById);
            updateEmptyStates();
        }, e -> {});
    }

    private void loadUsers() {
        profileService.getAllProfiles(users -> {
            if (!isAdded()) return;
            allUsers.clear();
            allUsers.addAll(users);
            displayedUsers.clear();
            displayedUsers.addAll(users);
            userAdapter.replaceUsers(displayedUsers, organizerUids);
            displayedOrganizers.clear();
            for (UserProfile user : allUsers) {
                if (organizerUids.contains(user.getUid())) {
                    displayedOrganizers.add(user);
                }
            }
            organizerAdapter.replaceUsers(displayedOrganizers, organizerUids);
            if (isOrganizerMode && filteredOrganizerUid != null) {
                for (UserProfile user : allUsers) {
                    if (filteredOrganizerUid.equals(user.getUid())) {
                        organizerName = user.getName();
                        updateTitleWithOrganizer();
                        break;
                    }
                }
            }
            updateEmptyStates();
        }, e -> {});
    }

    private void loadImages() {
        imageService.getAllImages(images -> {
            if (!isAdded()) return;
            List<Event> filtered = new ArrayList<>();
            for (Event event : images) {
                if (isOrganizerMode && filteredOrganizerUid != null) {
                    if (filteredOrganizerUid.equals(event.getOrganizerUid())) {
                        filtered.add(event);
                    }
                } else {
                    filtered.add(event);
                }
            }
            displayedImages.clear();
            displayedImages.addAll(filtered);
            imageAdapter.replaceEvents(displayedImages);
            updateEmptyStates();
        }, e -> {});
    }

    private void loadComments() {
        commentService.getAllCommentsForAdmin(comments -> {
            if (!isAdded()) return;
            displayedComments.clear();
            if (isOrganizerMode) {
                for (EventComment c : comments) {
                    String senderUid = c.getAuthorUid(); // Just for checking if we can filter by author too, but usually it's by event
                    for (Event e : allEvents) {
                        if (e.getEventId().equals(c.getEventId()) && filteredOrganizerUid.equals(e.getOrganizerUid())) {
                            displayedComments.add(c);
                            break;
                        }
                    }
                }
            } else {
                displayedComments.addAll(comments);
            }
            commentAdapter.replaceComments(displayedComments);
            updateEmptyStates();
        }, e -> {});
    }

    private void loadNotifications() {
        notificationRepository.getAllNotifications(records -> {
            if (!isAdded()) return;
            displayedNotifications.clear();
            if (isOrganizerMode && filteredOrganizerUid != null) {
                // Filter and Group
                Map<String, NotificationRecord> grouped = new HashMap<>();
                for (NotificationRecord record : records) {
                    if (filteredOrganizerUid.equals(record.getSenderUid())) {
                        // Grouping key: title + message + timestamp (approx)
                        String key = record.getTitle() + "|" + record.getMessage() + "|" + record.getSentAtMillis();
                        if (!grouped.containsKey(key)) {
                            grouped.put(key, record);
                        }
                    }
                }
                List<NotificationRecord> groupedList = new ArrayList<>(grouped.values());
                groupedList.sort((a, b) -> Long.compare(b.getSentAtMillis(), a.getSentAtMillis()));
                displayedNotifications.addAll(groupedList);
            } else {
                displayedNotifications.addAll(records);
            }
            notificationAdapter.replaceNotifications(displayedNotifications);
            updateEmptyStates();
        }, error -> {
            if (!isAdded()) return;
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
        new MaterialAlertDialogBuilder(requireContext())
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
            updateEmptyStates();
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
            displayedImages.remove(event);
            imageAdapter.replaceEvents(displayedImages);
            updateEmptyStates();
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

        new MaterialAlertDialogBuilder(requireContext())
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
            updateEmptyStates();
            toast("Notification log entry deleted.");
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Delete failed: " + error.getMessage());
        });
    }
    private void bindGeolocationLab() {
        if (layoutGeolocationLabToggle != null) {
            layoutGeolocationLabToggle.setOnClickListener(v -> {
                boolean isVisible = layoutGeolocationLabContent.getVisibility() == View.VISIBLE;
                HeliosUi.setVisible(layoutGeolocationLabContent, !isVisible);
                tvGeolocationLabToggleState.setText(isVisible ? "Show" : "Hide");
            });
        }
        if (btnGeolocationLabSeed != null) {
            btnGeolocationLabSeed.setOnClickListener(v -> refreshGeolocationSandbox());
        }
        if (btnGeolocationLabMenu != null) {
            btnGeolocationLabMenu.setOnClickListener(this::showGeolocationLabMenu);
        }
    }

    private void bindNotificationLab() {
        if (layoutNotificationLabToggle != null) {
            layoutNotificationLabToggle.setOnClickListener(v -> {
                boolean isVisible = layoutNotificationLabContent.getVisibility() == View.VISIBLE;
                HeliosUi.setVisible(layoutNotificationLabContent, !isVisible);
                tvNotificationLabToggleState.setText(isVisible ? "Show" : "Hide");
            });
        }
        if (btnNotificationLabSeed != null) {
            btnNotificationLabSeed.setOnClickListener(v -> refreshNotificationSandbox());
        }
        if (btnNotificationLabMenu != null) {
            btnNotificationLabMenu.setOnClickListener(this::showNotificationLabMenu);
        }
    }

    private void onEventDelete(@NonNull Event event) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Event")
                .setMessage("Are you sure you want to delete this event and all its data?")
                .setPositiveButton("Delete", (dialog, which) -> deleteEvent(event))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onUserDelete(@NonNull UserProfile user) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete this user and all their organized events?")
                .setPositiveButton("Delete", (dialog, which) -> deleteUser(user))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTitleWithOrganizer() {
        if (isOrganizerMode && filteredOrganizerUid != null) {
            profileService.getUserProfile(filteredOrganizerUid, profile -> {
                if (!isAdded()) return;
                String name = profile != null && profile.getName() != null ? profile.getName() : "Organizer";
                TextView titleView = requireView().findViewById(R.id.submenu_title);
                if (titleView != null) {
                    titleView.setText("Audit: " + name);
                }
            }, error -> {});
        }
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
        HeliosUi.setVisible(tvNoImages, imagesVisible && displayedImages.isEmpty());
    }

    private void updateNoCommentsState() {
        updateNoCommentsState(rvComments != null && rvComments.getVisibility() == View.VISIBLE);
    }

    private void updateNoCommentsState(boolean commentsVisible) {
        if (tvNoComments == null) {
            return;
        }
        HeliosUi.setVisible(tvNoComments, commentsVisible && displayedComments.isEmpty());
    }

    private void updateNoNotificationsState(boolean notificationsVisible) {
        if (tvNoNotifications == null) {
            return;
        }
        HeliosUi.setVisible(tvNoNotifications, notificationsVisible && displayedNotifications.isEmpty());
    }

    private void showOrganizerInfo(@NonNull Event event) {
        String organizerUid = event.getOrganizerUid();
        if (!isNonEmpty(organizerUid)) {
            new MaterialAlertDialogBuilder(requireContext())
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

            new MaterialAlertDialogBuilder(requireContext())
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
            new MaterialAlertDialogBuilder(requireContext())
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

        new MaterialAlertDialogBuilder(requireContext())
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
        new MaterialAlertDialogBuilder(requireContext())
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
