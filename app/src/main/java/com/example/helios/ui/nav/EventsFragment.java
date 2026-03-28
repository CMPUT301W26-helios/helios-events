package com.example.helios.ui.nav;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.ui.EventAdapter;
import com.example.helios.ui.event.EventDetailsBottomSheet;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fragment that displays a searchable and filterable list of all events.
 * Users can filter events by date range and interests, and search by title or description.
 */
public class EventsFragment extends Fragment {

    private RecyclerView rvEvents;
    private EditText etSearch;
    private View btnFilter;

    // Filter UI elements
    private View llDateFilters;
    private TextView tvFilterStartDate;
    private TextView tvFilterEndDate;
    private View llInterestFilters;
    private ChipGroup cgFilterDisplay;

    private EventAdapter eventAdapter;
    private final EventService eventService = new EventService();

    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> filteredEvents = new ArrayList<>();

    // Filter State
    private Long startDateFilter = null;
    private Long endDateFilter = null;
    private final List<String> selectedInterests = new ArrayList<>();
    private boolean loadedOnce = false;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    /**
     * Default constructor for EventsFragment.
     */
    public EventsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvEvents = view.findViewById(R.id.rv_events);
        etSearch = view.findViewById(R.id.et_search);
        btnFilter = view.findViewById(R.id.btn_filter);

        llDateFilters = view.findViewById(R.id.ll_date_filters);
        tvFilterStartDate = view.findViewById(R.id.tv_filter_start_date);
        tvFilterEndDate = view.findViewById(R.id.tv_filter_end_date);
        llInterestFilters = view.findViewById(R.id.ll_interest_filters);
        cgFilterDisplay = view.findViewById(R.id.cg_filter_display);

        setupRecyclerView();
        setupSearch();
        
        btnFilter.setOnClickListener(v -> showFilterDialog());
        view.findViewById(R.id.btn_clear_date_filter).setOnClickListener(v -> {
            startDateFilter = null;
            endDateFilter = null;
            applyFilters();
        });

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

    /**
     * Initializes the RecyclerView and its adapter.
     */
    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(filteredEvents, event -> {
            if (!isAdded()) return;

            String eventId = event.getEventId();
            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Event is missing an ID.", Toast.LENGTH_SHORT).show();
                return;
            }

            EventDetailsBottomSheet
                    .newInstance(eventId)
                    .show(getParentFragmentManager(), "event_details");
        });

        rvEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEvents.setAdapter(eventAdapter);
    }

    /**
     * Sets up the search bar with a debounced filter action.
     */
    private void setupSearch() {
        // Strip newline characters so the search field always stays single-line.
        InputFilter noNewLineFilter = (source, start, end, dest, dstart, dend) -> {
            if (source == null) return null;

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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingFilter != null) handler.removeCallbacks(pendingFilter);
                pendingFilter = this::runFilter;
                handler.postDelayed(pendingFilter, 200);
            }
            private void runFilter() { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Loads events from the {@link EventService}.
     */
    private void loadEvents() {
        eventService.getAllEvents(
                events -> {
                    if (!isAdded()) return;

                    loadedOnce = true;
                    allEvents.clear();
                    allEvents.addAll(events);

                    applyFilters();
                },
                e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Failed to load events: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
        );
    }

    /**
     * Displays a dialog for selecting filters (date range and interests).
     */
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_events, null);
        ChipGroup cgInterests = dialogView.findViewById(R.id.cg_interests);
        populateInterestFilterChips(cgInterests);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Apply", (d, which) -> {
                    selectedInterests.clear();
                    for (int id : cgInterests.getCheckedChipIds()) {
                        selectedInterests.add(((Chip) cgInterests.findViewById(id)).getText().toString());
                    }
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .create();

        Button btnStart = dialogView.findViewById(R.id.btn_start_date);
        Button btnEnd = dialogView.findViewById(R.id.btn_end_date);
        Button btnResetDateRange = dialogView.findViewById(R.id.btn_reset_date_filter_dialog);
        CharSequence defaultStartDateText = btnStart.getText();
        CharSequence defaultEndDateText = btnEnd.getText();

        if (startDateFilter != null) btnStart.setText(dateFormat.format(new Date(startDateFilter)));
        if (endDateFilter != null) btnEnd.setText(dateFormat.format(new Date(endDateFilter)));

        btnStart.setOnClickListener(v -> showDatePicker(
                startDateFilter != null ? startDateFilter : endDateFilter,
                null,
                endDateFilter,
                date -> {
            startDateFilter = date;
            btnStart.setText(dateFormat.format(new Date(date)));

            // Keep range valid: end date cannot be before start date.
            if (endDateFilter != null && endDateFilter < startDateFilter) {
                endDateFilter = startDateFilter;
                btnEnd.setText(dateFormat.format(new Date(endDateFilter)));
            }
        }));

        btnEnd.setOnClickListener(v -> showDatePicker(
                endDateFilter != null ? endDateFilter : startDateFilter,
                startDateFilter,
                null,
                date -> {
            endDateFilter = date;
            btnEnd.setText(dateFormat.format(new Date(date)));
        }));

        btnResetDateRange.setOnClickListener(v -> {
            startDateFilter = null;
            endDateFilter = null;
            btnStart.setText(defaultStartDateText);
            btnEnd.setText(defaultEndDateText);
        });

        dialog.show();
    }

    /**
     * Populates the interest filter chips using tags from all loaded events.
     */
    private void populateInterestFilterChips(@NonNull ChipGroup chipGroup) {
        chipGroup.removeAllViews();
        chipGroup.setSingleSelection(false);
        chipGroup.setSelectionRequired(false);

        int checkedBg = ContextCompat.getColor(requireContext(), R.color.helios_button_primary);
        int uncheckedBg = ContextCompat.getColor(requireContext(), R.color.helios_button_secondary);
        int checkedText = ContextCompat.getColor(requireContext(), R.color.helios_text_primary);
        int uncheckedText = ContextCompat.getColor(requireContext(), R.color.helios_text_hint);
        int checkedStroke = ContextCompat.getColor(requireContext(), R.color.helios_accent);
        int uncheckedStroke = ContextCompat.getColor(requireContext(), R.color.helios_button_secondary_border);

        ColorStateList backgroundColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedBg, uncheckedBg}
        );
        ColorStateList textColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedText, uncheckedText}
        );
        ColorStateList strokeColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedStroke, uncheckedStroke}
        );
        float strokeWidthPx = getResources().getDisplayMetrics().density;

        for (String interest : collectAvailableInterests()) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(interest);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(backgroundColors);
            chip.setTextColor(textColors);
            chip.setChipStrokeColor(strokeColors);
            chip.setChipStrokeWidth(strokeWidthPx);
            chip.setChecked(isInterestSelected(interest));
            chipGroup.addView(chip);
        }
    }

    /**
     * Collects unique, non-empty interests from all events and sorts them alphabetically.
     */
    @NonNull
    private List<String> collectAvailableInterests() {
        Map<String, String> uniqueByNormalized = new TreeMap<>();

        for (Event event : allEvents) {
            List<String> eventInterests = event.getInterests();
            if (eventInterests == null) continue;

            for (String rawInterest : eventInterests) {
                if (rawInterest == null) continue;

                String trimmed = rawInterest.trim();
                if (trimmed.isEmpty()) continue;

                String normalized = trimmed.toLowerCase(Locale.getDefault());
                uniqueByNormalized.putIfAbsent(normalized, trimmed);
            }
        }

        return new ArrayList<>(uniqueByNormalized.values());
    }

    private boolean isInterestSelected(@NonNull String interest) {
        for (String selected : selectedInterests) {
            if (selected != null && selected.equalsIgnoreCase(interest)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Displays a date picker dialog.
     *
     * @param initialDateMillis Date to show initially (nullable).
     * @param minDateMillis Minimum selectable date (nullable).
     * @param maxDateMillis Maximum selectable date (nullable).
     * @param listener Callback receiving the selected date in milliseconds.
     */
    private void showDatePicker(@Nullable Long initialDateMillis,
                                @Nullable Long minDateMillis,
                                @Nullable Long maxDateMillis,
                                OnDateSelectedListener listener) {
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

    /**
     * Applies the current search query and filters to the event list.
     */
    private void applyFilters() {
        filteredEvents.clear();
        String query = etSearch.getText().toString().toLowerCase().trim();

        for (Event event : allEvents) {
            boolean matchesSearch = query.isEmpty() || 
                    (event.getTitle() != null && event.getTitle().toLowerCase().contains(query)) ||
                    (event.getDescription() != null && event.getDescription().toLowerCase().contains(query));

            boolean matchesDate = true;
            if (startDateFilter != null && event.getStartTimeMillis() < startDateFilter) matchesDate = false;
            if (endDateFilter != null && event.getStartTimeMillis() > endDateFilter) matchesDate = false;

            boolean matchesInterests;
            if (selectedInterests.isEmpty()) {
                matchesInterests = true;
            } else {
                matchesInterests = false;
                java.util.List<String> eventInterests = event.getInterests();
                if (eventInterests != null && !eventInterests.isEmpty()) {
                    for (String selected : selectedInterests) {
                        String selectedLower = selected.trim().toLowerCase(Locale.getDefault());
                        for (String tag : eventInterests) {
                            if (tag != null
                                    && tag.trim().toLowerCase(Locale.getDefault()).equals(selectedLower)) {
                                matchesInterests = true;
                                break;
                            }
                        }
                        if (matchesInterests) break;
                    }
                }
            }
            
            if (matchesSearch && matchesDate && matchesInterests) {
                filteredEvents.add(event);
            }
        }

        updateFilterVisuals();
        eventAdapter.notifyDataSetChanged();
    }

    /**
     * Updates the UI to reflect the currently applied filters.
     */
    private void updateFilterVisuals() {
        if (startDateFilter != null && endDateFilter != null) {
            llDateFilters.setVisibility(View.VISIBLE);
            tvFilterStartDate.setText(dateFormat.format(new Date(startDateFilter)));
            tvFilterEndDate.setText(dateFormat.format(new Date(endDateFilter)));
        } else {
            llDateFilters.setVisibility(View.GONE);
        }

        cgFilterDisplay.removeAllViews();
        if (!selectedInterests.isEmpty()) {
            llInterestFilters.setVisibility(View.VISIBLE);
            for (String interest : selectedInterests) {
                Chip chip = new Chip(requireContext());
                chip.setText(interest);
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    selectedInterests.remove(interest);
                    applyFilters();
                });
                cgFilterDisplay.addView(chip);
            }
        } else {
            llInterestFilters.setVisibility(View.GONE);
        }
    }

    /**
     * Interface for date selection callbacks.
     */
    interface OnDateSelectedListener { 
        /**
         * Called when a date is selected.
         * @param date The selected date in milliseconds.
         */
        void onDateSelected(long date); 
    }
}
