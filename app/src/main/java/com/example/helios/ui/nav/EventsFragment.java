package com.example.helios.ui.nav;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.EntrantEventService;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.EventAdapter;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.common.HeliosChipFactory;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.HeliosUi;
import com.example.helios.ui.common.NotificationNavArgs;
import com.example.helios.ui.event.EventDetailsBottomSheet;
import com.example.helios.ui.event.EventDiscoveryInsights;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class EventsFragment extends Fragment {
    private static final String EVENT_DETAILS_TAG = "event_details";

    private RecyclerView rvEvents;
    private EditText etSearch;
    private View btnFilter;
    private Button btnAllTags;
    private Button btnTagSuggestionsToggle;
    private ChipGroup cgEventVisibility;
    private ChipGroup cgRegistrationAvailability;
    private ChipGroup cgCapacityFilters;
    private ChipGroup cgInterestPreview;
    private ChipGroup cgPopularPreview;
    private TextView tvResultsSummary;
    private View layoutFilterMenu;
    private View layoutSuggestedTagsContent;

    private View llDateFilters;
    private Button btnFilterStartDate;
    private Button btnFilterEndDate;
    private View btnClearDateFilter;
    private View llInterestFilters;
    private ChipGroup cgFilterDisplay;

    private EventAdapter eventAdapter;
    private EventService eventService;
    private EntrantEventService entrantEventService;
    private ProfileService profileService;

    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> filteredEvents = new ArrayList<>();
    private final Map<String, WaitingListStatus> currentUserEntryStatuses = new TreeMap<>();
    private final EventBrowseFilter browseFilter = new EventBrowseFilter();
    @Nullable
    private String currentUid;

    private boolean loadedOnce = false;
    private boolean filtersMenuExpanded = false;
    private boolean tagSuggestionsExpanded = false;
    private int availableTagSuggestionCount = 0;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    public EventsFragment() {}

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvHeaderTitle = view.findViewById(R.id.tvScreenTitle);
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setText("Discover Events");
        }

        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        entrantEventService = application.getEntrantEventService();
        profileService = application.getProfileService();

        rvEvents = view.findViewById(R.id.rv_events);
        etSearch = view.findViewById(R.id.et_search);
        btnFilter = view.findViewById(R.id.btn_filter);
        btnAllTags = view.findViewById(R.id.btn_all_tags);
        btnTagSuggestionsToggle = view.findViewById(R.id.btn_tag_suggestions_toggle);
        cgEventVisibility = view.findViewById(R.id.cg_event_visibility);
        cgRegistrationAvailability = view.findViewById(R.id.cg_registration_availability);
        cgCapacityFilters = view.findViewById(R.id.cg_capacity_filters);
        cgInterestPreview = view.findViewById(R.id.cg_interest_preview);
        cgPopularPreview = view.findViewById(R.id.cg_popular_preview);
        tvResultsSummary = view.findViewById(R.id.tv_results_summary);
        layoutFilterMenu = view.findViewById(R.id.layout_filter_menu);
        layoutSuggestedTagsContent = view.findViewById(R.id.layout_suggested_tags_content);

        llDateFilters = view.findViewById(R.id.layout_date_filters);
        btnFilterStartDate = view.findViewById(R.id.btn_filter_start_date);
        btnFilterEndDate = view.findViewById(R.id.btn_filter_end_date);
        btnClearDateFilter = view.findViewById(R.id.btn_clear_date_filter);
        llInterestFilters = view.findViewById(R.id.ll_interest_filters);
        cgFilterDisplay = view.findViewById(R.id.cg_filter_display);

        setupRecyclerView();
        setupSearch();
        setupVisibilityFilters();
        setupAvailabilityFilters();
        setupCapacityFilters();
        setupDateFilters();

        getParentFragmentManager().setFragmentResultListener(
                EventDetailsBottomSheet.RESULT_INVITATION_RESPONSE,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> handleInvitationResult(bundle)
        );
        getParentFragmentManager().setFragmentResultListener(
                NotificationNavArgs.REQUEST_OPEN_EVENT,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> handleNotificationOpen(bundle)
        );

        btnFilter.setOnClickListener(v -> toggleFilterMenu());
        btnAllTags.setOnClickListener(v -> showFilterDialog());
        btnTagSuggestionsToggle.setOnClickListener(v -> toggleTagSuggestions());

        updateFilterMenuVisibility();
        loadEvents();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (loadedOnce) {
            loadEvents();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingFilter != null) {
            handler.removeCallbacks(pendingFilter);
            pendingFilter = null;
        }
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(
                filteredEvents,
                this::openEventDetails,
                null,
                new EventAdapter.OnPrivateEventInviteActionListener() {
                    @Override
                    public void onAcceptInvite(@NonNull Event event) {
                        respondToPrivateInvite(event, WaitingListStatus.ACCEPTED, "Invitation accepted!");
                    }

                    @Override
                    public void onDeclineInvite(@NonNull Event event) {
                        respondToPrivateInvite(event, WaitingListStatus.DECLINED, "Invitation declined.");
                    }
                }
        );

        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);
    }

    private void setupSearch() {
        InputFilter noNewLineFilter = (source, start, end, dest, dstart, dend) -> {
            if (source == null) {
                return null;
            }

            StringBuilder cleaned = null;
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (c == '\n' || c == '\r') {
                    if (cleaned == null) {
                        cleaned = new StringBuilder(end - start);
                        cleaned.append(source, start, i);
                    }
                } else if (cleaned != null) {
                    cleaned.append(c);
                }
            }
            return cleaned == null ? null : cleaned.toString();
        };

        InputFilter[] existingFilters = etSearch.getFilters();
        InputFilter[] combinedFilters = Arrays.copyOf(existingFilters, existingFilters.length + 1);
        combinedFilters[existingFilters.length] = noNewLineFilter;
        etSearch.setFilters(combinedFilters);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingFilter != null) {
                    handler.removeCallbacks(pendingFilter);
                }
                pendingFilter = EventsFragment.this::applyFilters;
                handler.postDelayed(pendingFilter, 180);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupVisibilityFilters() {
        cgEventVisibility.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                group.check(R.id.chip_visibility_all);
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_visibility_public) {
                browseFilter.setVisibilityFilter(EventBrowseFilter.VisibilityFilter.PUBLIC_ONLY);
            } else if (checkedId == R.id.chip_visibility_private) {
                browseFilter.setVisibilityFilter(EventBrowseFilter.VisibilityFilter.PRIVATE_ONLY);
            } else if (checkedId == R.id.chip_visibility_joined) {
                browseFilter.setVisibilityFilter(EventBrowseFilter.VisibilityFilter.JOINED_ONLY);
            } else {
                browseFilter.setVisibilityFilter(EventBrowseFilter.VisibilityFilter.ALL);
            }
            applyFilters();
        });
    }

    private void setupAvailabilityFilters() {
        cgRegistrationAvailability.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                group.check(R.id.chip_availability_all);
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_availability_open) {
                browseFilter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.OPEN_NOW);
            } else if (checkedId == R.id.chip_availability_upcoming) {
                browseFilter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.UPCOMING);
            } else {
                browseFilter.setAvailabilityFilter(EventBrowseFilter.AvailabilityFilter.ALL);
            }
            applyFilters();
        });
    }

    private void setupCapacityFilters() {
        cgCapacityFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                group.check(R.id.chip_capacity_all);
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_capacity_limited) {
                browseFilter.setCapacityFilter(EventBrowseFilter.CapacityFilter.LIMITED_CAPACITY);
            } else if (checkedId == R.id.chip_capacity_waitlist) {
                browseFilter.setCapacityFilter(EventBrowseFilter.CapacityFilter.WAITLIST_LIMITED);
            } else {
                browseFilter.setCapacityFilter(EventBrowseFilter.CapacityFilter.ALL);
            }
            applyFilters();
        });
    }

    private void loadEvents() {
        profileService.ensureSignedIn(firebaseUser -> {
            currentUid = firebaseUser.getUid();
            eventService.getAllEvents(
                    events -> {
                        if (!isAdded()) {
                            return;
                        }
                        loadedOnce = true;
                        allEvents.clear();
                        allEvents.addAll(events);
                        loadCurrentUserEntries();
                    },
                    e -> {
                        if (!isAdded()) return;
                        HeliosUi.toast(this, "Failed to load events: " + e.getMessage());
                    }
            );
        }, e -> {
            if (!isAdded()) return;
            HeliosUi.toast(this, "Auth failed: " + e.getMessage());
        });
    }

    private void loadCurrentUserEntries() {
        entrantEventService.getCurrentUserWaitlistEntries(requireContext(), entries -> {
            if (!isAdded()) {
                return;
            }
            currentUserEntryStatuses.clear();
            for (WaitingListEntry entry : entries) {
                if (entry == null || entry.getStatus() == null) {
                    continue;
                }
                String eventId = entry.getEventId();
                if (eventId == null || eventId.trim().isEmpty()) {
                    continue;
                }
                currentUserEntryStatuses.put(eventId, entry.getStatus());
            }
            eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
            applyFilters();
        }, e -> {
            if (!isAdded()) {
                return;
            }
            loadCurrentUserEntriesFallback();
        });
    }

    private void loadCurrentUserEntriesFallback() {
        List<Event> privateEvents = new ArrayList<>();
        for (Event event : allEvents) {
            if (event != null && event.isPrivateEvent()) {
                privateEvents.add(event);
            }
        }

        currentUserEntryStatuses.clear();
        if (privateEvents.isEmpty()) {
            eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
            applyFilters();
            return;
        }

        final int[] remaining = {privateEvents.size()};
        for (Event event : privateEvents) {
            String eventId = event.getEventId();
            if (eventId == null || eventId.trim().isEmpty()) {
                remaining[0]--;
                if (remaining[0] == 0 && isAdded()) {
                    eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
                    applyFilters();
                }
                continue;
            }

            entrantEventService.getCurrentUserWaitingListEntry(requireContext(), eventId, entry -> {
                if (!isAdded()) {
                    return;
                }
                if (entry != null && entry.getStatus() != null) {
                    updateStoredEntryStatus(eventId, entry.getStatus());
                }
                remaining[0]--;
                if (remaining[0] == 0) {
                    eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
                    applyFilters();
                }
            }, error -> {
                if (!isAdded()) {
                    return;
                }
                remaining[0]--;
                if (remaining[0] == 0) {
                    eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
                    applyFilters();
                }
            });
        }
    }

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_filter_events, null);
        ChipGroup cgInterests = dialogView.findViewById(R.id.cg_interests);
        EditText etInterestSearch = dialogView.findViewById(R.id.et_interest_search);
        List<String> availableInterests = browseFilter.collectAvailableInterests(
                allEvents,
                currentUid,
                currentUserEntryStatuses
        );
        List<String> pendingInterests = new ArrayList<>(browseFilter.getSelectedInterests());
        populateInterestFilterChips(
                cgInterests,
                availableInterests,
                pendingInterests,
                ""
        );

        etInterestSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                populateInterestFilterChips(
                        cgInterests,
                        availableInterests,
                        pendingInterests,
                        s == null ? "" : s.toString()
                );
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Apply", (d, which) -> {
                    browseFilter.replaceSelectedInterests(pendingInterests);
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    private void populateInterestFilterChips(
            @NonNull ChipGroup chipGroup,
            @NonNull List<String> availableInterests,
            @NonNull List<String> selectedInterests,
            @NonNull String rawQuery
    ) {
        chipGroup.removeAllViews();
        chipGroup.setSingleSelection(false);
        chipGroup.setSelectionRequired(false);

        String query = rawQuery.trim().toLowerCase(Locale.getDefault());
        List<String> matchingInterests = new ArrayList<>();
        for (String interest : availableInterests) {
            if (query.isEmpty() || interest.toLowerCase(Locale.getDefault()).contains(query)) {
                matchingInterests.add(interest);
            }
        }

        if (matchingInterests.isEmpty()) {
            Chip emptyChip = HeliosChipFactory.createAssistChip(requireContext(), "No matching tags");
            styleCompactChip(emptyChip);
            emptyChip.setEnabled(false);
            chipGroup.addView(emptyChip);
            return;
        }

        for (String interest : matchingInterests) {
            Chip chip = HeliosChipFactory.createCheckableChip(
                    requireContext(),
                    interest,
                    containsIgnoreCase(selectedInterests, interest)
            );
            styleCompactChip(chip);
            chip.setOnCheckedChangeListener((buttonView, isChecked) ->
                    toggleInterest(selectedInterests, interest, isChecked));
            chipGroup.addView(chip);
        }
    }

    private void showDatePicker(
            @Nullable Long initialDateMillis,
            @Nullable Long minDateMillis,
            @Nullable Long maxDateMillis,
            @NonNull OnDateSelectedListener listener
    ) {
        Calendar cal = Calendar.getInstance();
        if (initialDateMillis != null) {
            cal.setTimeInMillis(initialDateMillis);
        } else if (minDateMillis != null) {
            cal.setTimeInMillis(minDateMillis);
        } else if (maxDateMillis != null && cal.getTimeInMillis() > maxDateMillis) {
            cal.setTimeInMillis(maxDateMillis);
        }

        DatePickerDialog picker = new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            cal.set(year, month, day, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            listener.onDateSelected(cal.getTimeInMillis());
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        if (minDateMillis != null) {
            Calendar minCal = Calendar.getInstance();
            minCal.setTimeInMillis(minDateMillis);
            minCal.set(Calendar.HOUR_OF_DAY, 0);
            minCal.set(Calendar.MINUTE, 0);
            minCal.set(Calendar.SECOND, 0);
            minCal.set(Calendar.MILLISECOND, 0);
            picker.getDatePicker().setMinDate(minCal.getTimeInMillis());
        }

        if (maxDateMillis != null) {
            Calendar maxCal = Calendar.getInstance();
            maxCal.setTimeInMillis(maxDateMillis);
            maxCal.set(Calendar.HOUR_OF_DAY, 23);
            maxCal.set(Calendar.MINUTE, 59);
            maxCal.set(Calendar.SECOND, 59);
            maxCal.set(Calendar.MILLISECOND, 999);
            picker.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
        }

        picker.show();
    }

    private List<String> buildInterestPreviewTags(
            @NonNull List<String> availableInterests,
            @NonNull EventDiscoveryInsights.Result insights
    ) {
        Set<String> orderedTags = new LinkedHashSet<>();
        for (String selected : browseFilter.getSelectedInterests()) {
            orderedTags.add(selected);
        }
        orderedTags.addAll(insights.getNewTags());
        orderedTags.addAll(availableInterests);
        if (orderedTags.isEmpty()) {
            orderedTags.addAll(insights.getHotTags());
        }

        List<String> previewTags = new ArrayList<>();
        for (String tag : orderedTags) {
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            previewTags.add(tag);
            if (previewTags.size() >= 10) {
                break;
            }
        }
        return previewTags;
    }

    private void setupDateFilters() {
        btnFilterStartDate.setOnClickListener(v -> showDatePicker(
                browseFilter.getStartDateFilter() != null
                        ? browseFilter.getStartDateFilter()
                        : browseFilter.getEndDateFilter(),
                null,
                browseFilter.getEndDateFilter(),
                date -> {
                    browseFilter.setStartDateFilter(date);
                    if (browseFilter.getEndDateFilter() != null
                            && browseFilter.getEndDateFilter() < browseFilter.getStartDateFilter()) {
                        browseFilter.setEndDateFilter(browseFilter.getStartDateFilter());
                    }
                    applyFilters();
                }
        ));

        btnFilterEndDate.setOnClickListener(v -> showDatePicker(
                browseFilter.getEndDateFilter() != null
                        ? browseFilter.getEndDateFilter()
                        : browseFilter.getStartDateFilter(),
                browseFilter.getStartDateFilter(),
                null,
                date -> {
                    browseFilter.setEndDateFilter(date);
                    applyFilters();
                }
        ));

        btnClearDateFilter.setOnClickListener(v -> {
            browseFilter.clearDateRange();
            applyFilters();
        });
    }

    private void populateInterestPreviewChips(
            @NonNull ChipGroup chipGroup,
            @NonNull List<String> tags,
            @NonNull String emptyLabel
    ) {
        chipGroup.removeAllViews();
        chipGroup.setSingleSelection(false);
        chipGroup.setSelectionRequired(false);

        if (tags.isEmpty()) {
            Chip chip = HeliosChipFactory.createAssistChip(requireContext(), emptyLabel);
            styleCompactChip(chip);
            chip.setEnabled(false);
            chipGroup.addView(chip);
            return;
        }

        for (String tag : tags) {
            Chip chip = HeliosChipFactory.createCheckableChip(
                    requireContext(),
                    tag,
                    browseFilter.isInterestSelected(tag)
            );
            stylePreviewChip(chip);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                browseFilter.toggleInterestSelection(tag, isChecked);
                applyFilters();
            });
            chipGroup.addView(chip);
        }
    }

    private void applyFilters() {
        EventBrowseFilter.Result result = browseFilter.apply(
                allEvents,
                currentUid,
                currentUserEntryStatuses,
                getSearchQuery()
        );
        filteredEvents.clear();
        filteredEvents.addAll(result.getFilteredEvents());
        updateFilterVisuals();
        updateResultsSummary(result);
        eventAdapter.replaceEvents(filteredEvents);
    }

    private void toggleFilterMenu() {
        filtersMenuExpanded = !filtersMenuExpanded;
        updateFilterMenuVisibility();
    }

    private void toggleTagSuggestions() {
        tagSuggestionsExpanded = !tagSuggestionsExpanded;
        updateTagSuggestionsVisibility(availableTagSuggestionCount);
    }

    private void updateFilterMenuVisibility() {
        if (layoutFilterMenu != null) {
            layoutFilterMenu.setVisibility(filtersMenuExpanded ? View.VISIBLE : View.GONE);
        }

        if (btnFilter instanceof Button) {
            ((Button) btnFilter).setText(buildFilterButtonLabel());
        }
        updateTagSuggestionsVisibility(availableTagSuggestionCount);
    }

    @NonNull
    private String buildFilterButtonLabel() {
        int activeCount = 0;
        if (!browseFilter.getSelectedInterests().isEmpty()) {
            activeCount++;
        }
        if (browseFilter.getStartDateFilter() != null || browseFilter.getEndDateFilter() != null) {
            activeCount++;
        }
        if (browseFilter.getVisibilityFilter() != EventBrowseFilter.VisibilityFilter.ALL) {
            activeCount++;
        }
        if (browseFilter.getAvailabilityFilter() != EventBrowseFilter.AvailabilityFilter.ALL) {
            activeCount++;
        }
        if (browseFilter.getCapacityFilter() != EventBrowseFilter.CapacityFilter.ALL) {
            activeCount++;
        }

        if (filtersMenuExpanded) {
            return activeCount > 0 ? "Hide (" + activeCount + ")" : "Hide";
        }
        return activeCount > 0 ? "Filters (" + activeCount + ")" : "Filters";
    }

    private void updateTagSuggestionsVisibility(int availableSuggestionCount) {
        if (layoutSuggestedTagsContent != null) {
            boolean showContent = filtersMenuExpanded
                    && tagSuggestionsExpanded
                    && availableSuggestionCount > 0;
            layoutSuggestedTagsContent.setVisibility(showContent ? View.VISIBLE : View.GONE);
        }
        if (btnTagSuggestionsToggle != null) {
            if (availableSuggestionCount <= 0) {
                btnTagSuggestionsToggle.setText("No suggestions");
                btnTagSuggestionsToggle.setEnabled(false);
            } else {
                btnTagSuggestionsToggle.setEnabled(true);
                btnTagSuggestionsToggle.setText(tagSuggestionsExpanded
                        ? "Hide suggestions"
                        : "Suggestions (" + availableSuggestionCount + ")");
            }
        }
    }

    private void updateFilterVisuals() {
        llDateFilters.setVisibility(View.VISIBLE);
        btnFilterStartDate.setText(browseFilter.getStartDateFilter() != null
                ? dateFormat.format(new Date(browseFilter.getStartDateFilter()))
                : "Start date");
        btnFilterEndDate.setText(browseFilter.getEndDateFilter() != null
                ? dateFormat.format(new Date(browseFilter.getEndDateFilter()))
                : "End date");
        btnClearDateFilter.setVisibility(
                browseFilter.getStartDateFilter() != null || browseFilter.getEndDateFilter() != null
                        ? View.VISIBLE
                        : View.GONE
        );

        cgFilterDisplay.removeAllViews();
        if (browseFilter.getSelectedInterests().isEmpty()) {
            llInterestFilters.setVisibility(View.GONE);
        } else {
            llInterestFilters.setVisibility(View.VISIBLE);
            for (String interest : browseFilter.getSelectedInterests()) {
                Chip chip = HeliosChipFactory.createDismissibleChip(
                        requireContext(),
                        interest,
                        v -> {
                            browseFilter.toggleInterestSelection(interest, false);
                            applyFilters();
                        }
                );
                styleSelectedFilterChip(chip);
                cgFilterDisplay.addView(chip);
            }
        }

        List<String> availableInterests = browseFilter.collectAvailableInterests(
                allEvents,
                currentUid,
                currentUserEntryStatuses
        );
        EventDiscoveryInsights.Result insights = EventDiscoveryInsights.fromEvents(
                browseFilter.collectDisplayableEvents(allEvents, currentUid, currentUserEntryStatuses),
                System.currentTimeMillis()
        );
        List<String> hotTags = insights.getHotTags();
        List<String> popularTags = buildInterestPreviewTags(availableInterests, insights);
        populateInterestPreviewChips(
                cgInterestPreview,
                hotTags,
                "No hot tags"
        );
        populateInterestPreviewChips(
                cgPopularPreview,
                popularTags,
                "No popular tags"
        );
        updateTagActionLabels(hotTags, popularTags);

        updateFilterMenuVisibility();
    }

    private void updateTagActionLabels(
            @NonNull List<String> hotTags,
            @NonNull List<String> popularTags
    ) {
        Set<String> suggestionTags = new LinkedHashSet<>();
        suggestionTags.addAll(hotTags);
        suggestionTags.addAll(popularTags);
        availableTagSuggestionCount = suggestionTags.size();
        updateTagSuggestionsVisibility(availableTagSuggestionCount);

        if (btnAllTags != null) {
            int selectedCount = browseFilter.getSelectedInterests().size();
            btnAllTags.setText(selectedCount > 0
                    ? "Browse tags (" + selectedCount + ")"
                    : "Browse tags");
        }
    }

    private void stylePreviewChip(@NonNull Chip chip) {
        styleCompactChip(chip);
        chip.setCheckedIconVisible(false);
    }

    private void styleSelectedFilterChip(@NonNull Chip chip) {
        styleCompactChip(chip);
    }

    private void styleCompactChip(@NonNull Chip chip) {
        float density = chip.getResources().getDisplayMetrics().density;
        chip.setChipMinHeight(34f * density);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setTextSize(12f);
    }

    private void updateResultsSummary(@NonNull EventBrowseFilter.Result result) {
        String summary;
        if (filteredEvents.isEmpty()) {
            if (browseFilter.isJoinedFilter()) {
                summary = "You haven't joined any events yet";
            } else {
                summary = hasActiveBrowseInputs()
                        ? "No events match your search or filters"
                        : "No events to show yet";
            }
        } else if (browseFilter.isJoinedFilter()) {
            summary = "Your events: " + formatCount(filteredEvents.size(), "event", "events");
        } else {
            summary = "Showing " + formatCount(filteredEvents.size(), "event", "events");
        }
        if (result.getPrivateCount() > 0) {
            summary += " | " + formatCount(result.getPrivateCount(), "private invite", "private invites");
        }
        if (!browseFilter.getSelectedInterests().isEmpty()) {
            summary += " | " + formatCount(
                    browseFilter.getSelectedInterests().size(),
                    "tag filter",
                    "tag filters"
            );
        }
        tvResultsSummary.setText(summary);
    }

    private boolean hasActiveBrowseInputs() {
        return !getSearchQuery().isEmpty()
                || !browseFilter.getSelectedInterests().isEmpty()
                || browseFilter.getStartDateFilter() != null
                || browseFilter.getEndDateFilter() != null
                || browseFilter.getVisibilityFilter() != EventBrowseFilter.VisibilityFilter.ALL
                || browseFilter.getAvailabilityFilter() != EventBrowseFilter.AvailabilityFilter.ALL
                || browseFilter.getCapacityFilter() != EventBrowseFilter.CapacityFilter.ALL;
    }

    @NonNull
    private String getSearchQuery() {
        return etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
    }

    @NonNull
    private String formatCount(int count, @NonNull String singular, @NonNull String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private boolean containsIgnoreCase(@NonNull List<String> interests, @NonNull String target) {
        for (String interest : interests) {
            if (interest != null && interest.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private void toggleInterest(
            @NonNull List<String> selectedInterests,
            @NonNull String interest,
            boolean shouldSelect
    ) {
        if (shouldSelect) {
            if (!containsIgnoreCase(selectedInterests, interest)) {
                selectedInterests.add(interest);
            }
            return;
        }

        for (int i = selectedInterests.size() - 1; i >= 0; i--) {
            String selected = selectedInterests.get(i);
            if (selected != null && selected.equalsIgnoreCase(interest)) {
                selectedInterests.remove(i);
            }
        }
    }

    private void openEventDetails(@NonNull Event event) {
        if (!isAdded()) {
            return;
        }

        String eventId = event.getEventId();
        if (!HeliosText.isNonEmpty(eventId)) {
            HeliosUi.toast(this, "Event is missing an ID.");
            return;
        }

        openEventDetailsById(eventId);
    }

    private void handleNotificationOpen(@NonNull Bundle bundle) {
        String eventId = EventNavArgs.getEventId(bundle);
        if (!HeliosText.isNonEmpty(eventId)) {
            return;
        }
        openEventDetailsById(eventId);
    }

    private void openEventDetailsById(@NonNull String eventId) {
        if (!isAdded()) {
            return;
        }

        androidx.fragment.app.Fragment existing =
                getParentFragmentManager().findFragmentByTag(EVENT_DETAILS_TAG);
        if (existing instanceof EventDetailsBottomSheet) {
            ((EventDetailsBottomSheet) existing).dismissAllowingStateLoss();
        }

        EventDetailsBottomSheet.newInstance(eventId)
                .show(getParentFragmentManager(), EVENT_DETAILS_TAG);
    }

    private void respondToPrivateInvite(
            @NonNull Event event,
            @NonNull WaitingListStatus newStatus,
            @NonNull String successMessage
    ) {
        String eventId = event.getEventId();
        if (!HeliosText.isNonEmpty(eventId)) {
            HeliosUi.toast(this, "Event is missing an ID.");
            return;
        }

        entrantEventService.getCurrentUserWaitingListEntry(requireContext(), eventId, entry -> {
            if (!isAdded()) {
                return;
            }
            if (entry == null || entry.getStatus() != WaitingListStatus.INVITED) {
                HeliosUi.toast(this, "Invite no longer exists.");
                currentUserEntryStatuses.remove(eventId);
                eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
                applyFilters();
                return;
            }

            entry.setStatus(newStatus);
            entry.setRespondedAtMillis(System.currentTimeMillis());
            entrantEventService.updateEntry(requireContext(), eventId, entry,
                    unused -> {
                        if (!isAdded()) {
                            return;
                        }
                        updateStoredEntryStatus(eventId, newStatus);
                        eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
                        applyFilters();
                        HeliosUi.toast(this, successMessage);
                    },
                    e -> {
                        if (!isAdded()) return;
                        HeliosUi.toast(this, "Failed to save response: " + e.getMessage());
                    });
        }, e -> {
            if (!isAdded()) return;
            HeliosUi.toast(this, "Failed to load invite: " + e.getMessage());
        });
    }

    private void handleInvitationResult(@NonNull Bundle bundle) {
        String eventId = bundle.getString(EventDetailsBottomSheet.RESULT_EVENT_ID);
        String statusName = bundle.getString(EventDetailsBottomSheet.RESULT_STATUS);
        if (eventId == null || statusName == null) {
            return;
        }

        try {
            WaitingListStatus status = WaitingListStatus.valueOf(statusName);
            updateStoredEntryStatus(eventId, status);
            eventAdapter.setEntrantViewerState(currentUid, currentUserEntryStatuses);
            applyFilters();
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed results from older sheets.
        }
    }

    private void updateStoredEntryStatus(
            @NonNull String eventId,
            @NonNull WaitingListStatus status
    ) {
        if (status == WaitingListStatus.INVITED || status == WaitingListStatus.ACCEPTED) {
            currentUserEntryStatuses.put(eventId, status);
        } else {
            currentUserEntryStatuses.remove(eventId);
        }
    }

    interface OnDateSelectedListener {
        void onDateSelected(long date);
    }
}
