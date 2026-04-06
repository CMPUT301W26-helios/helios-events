package com.example.helios.ui.nav;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.EventAdapter;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.HeliosUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class OrganizeFragment extends Fragment {

    private EventService eventService;
    private ProfileService profileService;
    private WaitingListService waitingListService;

    private final List<Event> allOrganizerEvents = new ArrayList<>();
    private final List<Event> currentEvents = new ArrayList<>();
    private final List<Event> pastEvents = new ArrayList<>();

    private EventAdapter currentAdapter;
    private EventAdapter pastAdapter;
    @Nullable
    private String organizerUid;

    private EditText searchEditText;
    private TextView currentEmptyText;
    private TextView pastEmptyText;
    private Button createEventButton;

    private View cardCurrentEvents;
    private View cardPastEvents;
    private ImageView ivCurrentEventsIcon;
    private ImageView ivPastEventsIcon;
    private boolean currentEventsExpanded = true;
    private boolean pastEventsExpanded = true;

    public OrganizeFragment() {
        super(R.layout.fragment_organize);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView tvHeaderTitle = view.findViewById(R.id.tvScreenTitle);
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText("Organize Events");
        }

        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        profileService = application.getProfileService();
        waitingListService = application.getWaitingListService();
        createEventButton = view.findViewById(R.id.button_seed_demo_event);
        createEventButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.createEventFragment)
        );

        searchEditText = view.findViewById(R.id.et_search_my_events);
        currentEmptyText = view.findViewById(R.id.tv_current_events_empty);
        pastEmptyText = view.findViewById(R.id.tv_past_events_empty);

        RecyclerView rvCurrent = view.findViewById(R.id.rv_current_events);
        rvCurrent.setLayoutManager(new LinearLayoutManager(requireContext()));
        currentAdapter = new EventAdapter(
                currentEvents,
                this::openManagedEvent,
                new EventAdapter.OnCoOrganizerInviteActionListener() {
                    @Override
                    public void onAcceptInvite(@NonNull Event event) {
                        acceptCoOrganizerInvite(event);
                    }

                    @Override
                    public void onDeclineInvite(@NonNull Event event) {
                        declineCoOrganizerInvite(event);
                    }
                }
        );
        rvCurrent.setAdapter(currentAdapter);

        RecyclerView rvPast = view.findViewById(R.id.rv_past_events);
        if (rvPast != null) {
            rvPast.setLayoutManager(new LinearLayoutManager(requireContext()));
            pastAdapter = new EventAdapter(
                    pastEvents,
                    this::openManagedEvent,
                    null
            );
            rvPast.setAdapter(pastAdapter);
        }

        cardCurrentEvents = view.findViewById(R.id.card_current_events);
        cardPastEvents = view.findViewById(R.id.card_past_events);
        ivCurrentEventsIcon = view.findViewById(R.id.iv_current_events_icon);
        ivPastEventsIcon = view.findViewById(R.id.iv_past_events_icon);

        LinearLayout llCurrentHeader = view.findViewById(R.id.ll_current_events_header);
        if (llCurrentHeader != null) {
            llCurrentHeader.setOnClickListener(v -> toggleSection(true));
        }
        LinearLayout llPastHeader = view.findViewById(R.id.ll_past_events_header);
        if (llPastHeader != null) {
            llPastHeader.setOnClickListener(v -> toggleSection(false));
        }

        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyOrganizerFilter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        loadOrganizerEvents();
    }

    private void toggleSection(boolean isCurrent) {
        if (isCurrent) {
            currentEventsExpanded = !currentEventsExpanded;
            if (cardCurrentEvents != null) HeliosUi.setVisible(cardCurrentEvents, currentEventsExpanded);
            if (ivCurrentEventsIcon != null) ivCurrentEventsIcon.setImageResource(
                    currentEventsExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        } else {
            pastEventsExpanded = !pastEventsExpanded;
            if (cardPastEvents != null) HeliosUi.setVisible(cardPastEvents, pastEventsExpanded);
            if (ivPastEventsIcon != null) ivPastEventsIcon.setImageResource(
                    pastEventsExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        }
    }

    private void loadOrganizerEvents() {
        profileService.ensureSignedIn(firebaseUser -> {
            organizerUid = firebaseUser.getUid();
            profileService.getUserProfile(organizerUid, profile -> {
                if (!isAdded()) return;
                boolean organizerAccessRevoked = profile != null && profile.isOrganizerAccessRevoked();
                if (createEventButton != null) {
                    createEventButton.setEnabled(!organizerAccessRevoked);
                    createEventButton.setAlpha(organizerAccessRevoked ? 0.5f : 1f);
                }
                if (organizerAccessRevoked) {
                    allOrganizerEvents.clear();
                    currentEvents.clear();
                    pastEvents.clear();
                    if (currentAdapter != null) {
                        currentAdapter.replaceEvents(currentEvents);
                    }
                    if (pastAdapter != null) {
                        pastAdapter.replaceEvents(pastEvents);
                    }
                    updateEmptyStates();
                    HeliosUi.toastLong(this, "Organizer access is restricted for this profile.");
                    return;
                }

                if (currentAdapter != null) {
                    currentAdapter.setOrganizerViewerUid(organizerUid);
                }
                if (pastAdapter != null) {
                    pastAdapter.setOrganizerViewerUid(organizerUid);
                }

                eventService.getAllEvents(events -> {
                    if (!isAdded()) return;

                    allOrganizerEvents.clear();
                    for (Event e : events) {
                        if (isManagedByCurrentOrganizer(e)) {
                            allOrganizerEvents.add(e);
                        }
                    }

                    String query = searchEditText != null && searchEditText.getText() != null
                            ? searchEditText.getText().toString()
                            : "";

                    applyOrganizerFilter(query);

                }, error -> {
                    if (!isAdded()) return;
                    HeliosUi.toastLong(this, "Failed to load organizer events: " + error.getMessage());
                });
            }, error -> {
                if (!isAdded()) return;
                HeliosUi.toastLong(this, "Failed to load organizer profile: " + error.getMessage());
            });

        }, error -> {
            if (!isAdded()) return;
            HeliosUi.toastLong(this, "Auth failed: " + error.getMessage());
        });
    }

    private boolean isManagedByCurrentOrganizer(@NonNull Event event) {
        return organizerUid != null
                && (organizerUid.equals(event.getOrganizerUid())
                || event.isCoOrganizer(organizerUid)
                || event.isPendingCoOrganizer(organizerUid));
    }

    private void applyOrganizerFilter(@NonNull String query) {
        currentEvents.clear();
        pastEvents.clear();

        String normalizedQuery = query.trim().toLowerCase(Locale.CANADA);
        long now = System.currentTimeMillis();

        for (Event event : allOrganizerEvents) {
            if (!matchesQuery(event, normalizedQuery)) {
                continue;
            }

            if (organizerUid != null && event.isPendingCoOrganizer(organizerUid)) {
                currentEvents.add(event);
                continue;
            }

            long endTime = event.getEndTimeMillis();
            if (endTime > 0 && endTime < now) {
                pastEvents.add(event);
            } else {
                currentEvents.add(event);
            }
        }

        sortOrganizerEvents(currentEvents, true);
        sortOrganizerEvents(pastEvents, false);

        if (currentAdapter != null) {
            currentAdapter.replaceEvents(currentEvents);
        }
        if (pastAdapter != null) {
            pastAdapter.replaceEvents(pastEvents);
        }

        updateEmptyStates();
    }

    private boolean matchesQuery(Event event, @NonNull String query) {
        if (query.isEmpty()) {
            return true;
        }

        String title = HeliosText.safeLower(event.getTitle());
        String description = HeliosText.safeLower(event.getDescription());
        String location = HeliosText.safeLower(event.getLocationName());
        String address = HeliosText.safeLower(event.getAddress());

        return title.contains(query)
                || description.contains(query)
                || location.contains(query)
                || address.contains(query);
    }

    private void sortOrganizerEvents(
            @NonNull List<Event> events,
            boolean prioritizePendingInvites
    ) {
        Collections.sort(events, (left, right) -> {
            if (prioritizePendingInvites) {
                int inviteComparison = Boolean.compare(!isPendingInvite(left), !isPendingInvite(right));
                if (inviteComparison != 0) return inviteComparison;
            }
            long leftStart = left.getStartTimeMillis();
            long rightStart = right.getStartTimeMillis();
            if (leftStart <= 0 && rightStart <= 0) return compareTitles(left, right);
            if (leftStart <= 0) return 1;
            if (rightStart <= 0) return -1;
            int startComparison = Long.compare(leftStart, rightStart);
            return startComparison != 0 ? startComparison : compareTitles(left, right);
        });
    }

    private boolean isPendingInvite(@NonNull Event event) {
        return organizerUid != null && event.isPendingCoOrganizer(organizerUid);
    }

    private int compareTitles(@NonNull Event left, @NonNull Event right) {
        String leftTitle = left.getTitle() == null ? "" : left.getTitle().trim();
        String rightTitle = right.getTitle() == null ? "" : right.getTitle().trim();
        return leftTitle.compareToIgnoreCase(rightTitle);
    }

    private void updateEmptyStates() {
        if (currentEmptyText != null) {
            HeliosUi.setVisible(currentEmptyText, currentEvents.isEmpty());
        }
        if (pastEmptyText != null) {
            HeliosUi.setVisible(pastEmptyText, pastEvents.isEmpty());
        }
    }

    private void openManagedEvent(@NonNull Event event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            HeliosUi.toast(this, "Missing event id.");
            return;
        }
        NavHostFragment.findNavController(this)
                .navigate(R.id.manageEventFragment, EventNavArgs.forEventId(event.getEventId()));
    }

    private void acceptCoOrganizerInvite(@NonNull Event event) {
        if (organizerUid == null || event.getEventId() == null) return;

        List<String> pending = event.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(organizerUid)) {
            HeliosUi.toast(this, "Invite no longer exists.");
            loadOrganizerEvents();
            return;
        }

        List<String> coOrganizers = event.getCoOrganizerUids();
        if (coOrganizers == null) coOrganizers = new ArrayList<>();
        if (!coOrganizers.contains(organizerUid)) {
            coOrganizers.add(organizerUid);
        }

        event.setPendingCoOrganizerUids(pending);
        event.setCoOrganizerUids(coOrganizers);

        eventService.saveEvent(event, unused -> {
            if (!isAdded()) return;
            waitingListService.removeEntry(event.getEventId(), organizerUid, unused2 -> {
                if (!isAdded()) return;
                HeliosUi.toast(this, "Co-organizer invite accepted!");
                loadOrganizerEvents();
            }, error -> {
                if (!isAdded()) return;
                HeliosUi.toastLong(this, "Accepted, but could not remove waiting list entry: " + error.getMessage());
                loadOrganizerEvents();
            });
        }, error -> {
            if (!isAdded()) return;
            HeliosUi.toastLong(this, "Failed to accept invite: " + error.getMessage());
        });
    }

    private void declineCoOrganizerInvite(@NonNull Event event) {
        if (organizerUid == null) return;

        List<String> pending = event.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(organizerUid)) {
            HeliosUi.toast(this, "Invite no longer exists.");
            loadOrganizerEvents();
            return;
        }

        event.setPendingCoOrganizerUids(pending);
        eventService.saveEvent(event, unused -> {
            if (!isAdded()) return;
            HeliosUi.toast(this, "Co-organizer invite declined.");
            loadOrganizerEvents();
        }, error -> {
            if (!isAdded()) return;
            HeliosUi.toastLong(this, "Failed to decline invite: " + error.getMessage());
        });
    }
}
