package com.example.helios.ui.nav;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

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

    @Override
    public void onResume() {
        super.onResume();
        // Refresh list after returning from other screens (optional, but useful during dev)
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
        loadEvents();
    }

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

    private void setupSearch() {
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

    private void loadEvents() {
        eventService.getAllEvents(
                events -> {
                    if (!isAdded()) return;

                    loadedOnce = true;

                    allEvents.clear();
                    allEvents.addAll(events);

                    String currentQuery = etSearch.getText() != null ? etSearch.getText().toString() : "";
                    filterEvents(currentQuery);
                },
                e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Failed to load events: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
        );
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_events, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Apply", (d, which) -> {
                    ChipGroup cg = dialogView.findViewById(R.id.cg_interests);
                    selectedInterests.clear();
                    for (int id : cg.getCheckedChipIds()) {
                        selectedInterests.add(((Chip) cg.findViewById(id)).getText().toString());
                    }
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .create();

        Button btnStart = dialogView.findViewById(R.id.btn_start_date);
        Button btnEnd = dialogView.findViewById(R.id.btn_end_date);

        if (startDateFilter != null) btnStart.setText(dateFormat.format(new Date(startDateFilter)));
        if (endDateFilter != null) btnEnd.setText(dateFormat.format(new Date(endDateFilter)));

        btnStart.setOnClickListener(v -> showDatePicker(date -> {
            startDateFilter = date;
            btnStart.setText(dateFormat.format(new Date(date)));
        }));

        btnEnd.setOnClickListener(v -> showDatePicker(date -> {
            endDateFilter = date;
            btnEnd.setText(dateFormat.format(new Date(date)));
        }));

        dialog.show();
    }

    private void showDatePicker(OnDateSelectedListener listener) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            cal.set(year, month, day, 0, 0, 0);
            listener.onDateSelected(cal.getTimeInMillis());
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadEvents() {
        eventService.getAllEvents(events -> {
            if (!isAdded()) return;
            allEvents.clear();
            allEvents.addAll(events);
            applyFilters();
        }, e -> Toast.makeText(requireContext(), "Load failed", Toast.LENGTH_SHORT).show());
    }

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

            // Note: Currently Event model doesn't have interests/tags, assuming this matches title/desc for demo
            // or placeholder for future field.
            boolean matchesInterests = selectedInterests.isEmpty();
            
            if (matchesSearch && matchesDate && matchesInterests) {
                filteredEvents.add(event);
            }
        }

        updateFilterVisuals();
        eventAdapter.notifyDataSetChanged();
    }

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

    interface OnDateSelectedListener { void onDateSelected(long date); }
}