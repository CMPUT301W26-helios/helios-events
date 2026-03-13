package com.example.helios.ui.nav;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class OrganizerViewEntrantsFragment extends Fragment {

    private String eventId;
    private Event event;
    private final EventService eventService = new EventService();
    private final WaitingListService waitingListService = new WaitingListService();
    private final ProfileService profileService = new ProfileService();

    private final List<WaitingListEntry> allEntries = new ArrayList<>();
    private final List<EntrantDisplayModel> displayList = new ArrayList<>();
    private EntrantAdapter adapter;
    private boolean removeEntrantMode = false;

    private TextView tvDrawIndicator, tvListHeader, tvInfoStats;
    private View llFilters, layoutDrawControls, layoutDeclinedActions, layoutInvitedActions;
    private EditText etSearch;
    private TextView tvDrawCount;
    private MaterialButton btnRemoveEntrant;
    private ChipGroup cgStatus;

    public OrganizerViewEntrantsFragment() {
        super(R.layout.fragment_organizer_view_entrants);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString("arg_event_id");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView(view);
        setupListeners(view);
        loadData();
    }

    private void initViews(View view) {
        tvDrawIndicator = view.findViewById(R.id.tv_draw_indicator);
        tvListHeader = view.findViewById(R.id.tv_list_header);
        tvInfoStats = view.findViewById(R.id.tv_info_stats);
        llFilters = view.findViewById(R.id.ll_filters);
        layoutDrawControls = view.findViewById(R.id.layout_draw_controls);
        layoutDeclinedActions = view.findViewById(R.id.layout_declined_actions);
        layoutInvitedActions = view.findViewById(R.id.layout_invited_actions);
        etSearch = view.findViewById(R.id.et_search);
        tvDrawCount = view.findViewById(R.id.et_draw_count);
        btnRemoveEntrant = view.findViewById(R.id.btn_remove_entrant);
        cgStatus = view.findViewById(R.id.cg_status);
        updateRemoveEntrantButtonState();
    }

    private void setupRecyclerView(View view) {
        RecyclerView rv = view.findViewById(R.id.rv_entrants);
        adapter = new EntrantAdapter(displayList);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateDisplayList(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        cgStatus.setOnCheckedStateChangeListener((group, checkedIds) -> updateDisplayList());

        view.findViewById(R.id.btn_draw).setOnClickListener(v -> performDraw());
        
        view.findViewById(R.id.btn_delete_declined).setOnClickListener(v -> deleteSelectedDeclined());
        view.findViewById(R.id.btn_redraw_replacement).setOnClickListener(v -> redrawReplacements());
        btnRemoveEntrant.setOnClickListener(v -> toggleRemoveEntrantMode());
    }

    private void loadData() {
        if (eventId == null) return;

        eventService.getEventById(eventId, loadedEvent -> {
            this.event = loadedEvent;
            refreshEntries();
        }, e -> toast("Failed to load event"));
    }

    private void refreshEntries() {
        waitingListService.getEntriesForEvent(eventId, entries -> {
            allEntries.clear();
            allEntries.addAll(entries);
            updateDrawCountDisplay();
            updateUiState();
            updateDisplayList();
        }, e -> toast("Failed to load entrants"));
    }

    private void updateUiState() {
        boolean drawn = event != null && event.isDrawHappened();
        tvDrawIndicator.setVisibility(drawn ? View.VISIBLE : View.GONE);
        llFilters.setVisibility(drawn ? View.VISIBLE : View.GONE);
        tvListHeader.setText(drawn ? "Entrant Status" : "Waiting List");
        layoutDrawControls.setVisibility(View.VISIBLE);
        layoutInvitedActions.setVisibility(drawn ? View.VISIBLE : View.GONE);
        
        if (drawn && cgStatus.getCheckedChipId() == View.NO_ID) {
            cgStatus.check(R.id.chip_invited);
        }
    }

    private void updateDisplayList() {
        displayList.clear();
        String query = etSearch.getText().toString().toLowerCase(Locale.CANADA);
        boolean drawn = event != null && event.isDrawHappened();
        int checkedId = cgStatus.getCheckedChipId();
        boolean invitedFilter = drawn && checkedId == R.id.chip_invited;

        List<WaitingListEntry> filtered = allEntries.stream().filter(entry -> {
            if (!drawn) return entry.getStatus() == WaitingListStatus.WAITING;
            
            if (checkedId == R.id.chip_invited) 
                return entry.getStatus() == WaitingListStatus.INVITED || entry.getStatus() == WaitingListStatus.ACCEPTED;
            if (checkedId == R.id.chip_waitlisted)
                return entry.getStatus() == WaitingListStatus.NOT_SELECTED;
            if (checkedId == R.id.chip_declined)
                return entry.getStatus() == WaitingListStatus.DECLINED || entry.getStatus() == WaitingListStatus.CANCELLED;
            
            return false;
        }).collect(Collectors.toList());

        layoutDeclinedActions.setVisibility(drawn && checkedId == R.id.chip_declined ? View.VISIBLE : View.GONE);
        btnRemoveEntrant.setVisibility(invitedFilter ? View.VISIBLE : View.GONE);
        if (!invitedFilter && removeEntrantMode) {
            removeEntrantMode = false;
        }
        updateRemoveEntrantButtonState();

        // Update stats
        if (invitedFilter) {
            long confirmed = filtered.stream().filter(e -> e.getStatus() == WaitingListStatus.ACCEPTED).count();
            tvInfoStats.setText(confirmed + "/" + filtered.size() + " people confirmed");
            tvInfoStats.setVisibility(View.VISIBLE);
        } else if (drawn && checkedId == R.id.chip_waitlisted) {
            tvInfoStats.setText(filtered.size() + " people");
            tvInfoStats.setVisibility(View.VISIBLE);
        } else if (drawn && checkedId == R.id.chip_declined) {
            tvInfoStats.setText("Select entrants to delete or redraw");
            tvInfoStats.setVisibility(View.VISIBLE);
        } else {
            tvInfoStats.setVisibility(View.GONE);
        }

        // Fetch names and populate displayList
        for (WaitingListEntry entry : filtered) {
            EntrantDisplayModel model = new EntrantDisplayModel(entry);
            displayList.add(model);
            profileService.getUserProfile(entry.getEntrantUid(), profile -> {
                if (profile != null) {
                    model.name = profile.getName();
                    adapter.notifyDataSetChanged();
                }
            }, e -> {});
        }
        adapter.notifyDataSetChanged();
    }

    private void performDraw() {
        if (event == null) {
            toast("Event not loaded");
            return;
        }

        int count = event.getCapacity();
        if (count <= 0) {
            toast("Event capacity must be greater than 0");
            return;
        }

        long currentlySelected = allEntries.stream()
                .filter(e -> e.getStatus() == WaitingListStatus.INVITED
                        || e.getStatus() == WaitingListStatus.ACCEPTED)
                .count();
        int remainingSlots = count - (int) currentlySelected;
        if (remainingSlots <= 0) {
            toast("Selected entrants already reached event capacity");
            return;
        }

        List<WaitingListEntry> candidates = allEntries.stream()
                .filter(e -> e.getStatus() == WaitingListStatus.WAITING)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            toast("No entrants in waiting list");
            return;
        }

        Collections.shuffle(candidates);
        int toSelect = Math.min(remainingSlots, candidates.size());

        for (int i = 0; i < candidates.size(); i++) {
            WaitingListEntry entry = candidates.get(i);
            if (i < toSelect) {
                entry.setStatus(WaitingListStatus.INVITED);
            } else {
                entry.setStatus(WaitingListStatus.NOT_SELECTED);
            }
            waitingListService.updateEntry(eventId, entry, unused -\u003e {}, e -\u003e {});
        }

        event.setDrawHappened(true);
        eventService.saveEvent(event, unused -> {
            toast("Draw completed!");
            refreshEntries();
        }, e -> toast("Failed to update event status"));
    }

    private void toggleRemoveEntrantMode() {
        removeEntrantMode = !removeEntrantMode;
        updateRemoveEntrantButtonState();
        adapter.notifyDataSetChanged();
    }

    private void updateRemoveEntrantButtonState() {
        if (btnRemoveEntrant == null) return;
        btnRemoveEntrant.setText(removeEntrantMode ? "Done" : "Remove Entrant");
    }

    private void removeEntrant(@NonNull EntrantDisplayModel model) {
        String entrantUid = model.entry.getEntrantUid();
        if (entrantUid == null || entrantUid.trim().isEmpty()) {
            toast("Unable to remove entrant");
            return;
        }

        waitingListService.removeEntry(eventId, entrantUid, unused -> {
            String name = model.name != null ? model.name : "Entrant";
            toast(name + " removed");
            refreshEntries();
        }, e -> toast("Failed to remove entrant"));
    }

    private void updateDrawCountDisplay() {
        long selectedCount = allEntries.stream()
                .filter(e -> e.getStatus() == WaitingListStatus.INVITED
                        || e.getStatus() == WaitingListStatus.ACCEPTED)
                .count();
        tvDrawCount.setText(String.valueOf(selectedCount));
    }

    private void deleteSelectedDeclined() {
        Set<String> selectedUids = adapter.getSelectedUids();
        if (selectedUids.isEmpty()) {
            toast("No entrants selected");
            return;
        }
        for (String uid : selectedUids) {
            waitingListService.removeEntry(eventId, uid, unused -\u003e {}, e -\u003e {});
        }
        toast("Deleted selected entrants");
        refreshEntries();
    }

    private void redrawReplacements() {
        Set<String> selectedUids = adapter.getSelectedUids();
        if (selectedUids.isEmpty()) {
            toast("No entrants selected");
            return;
        }
        
        List<WaitingListEntry> waitlisted = allEntries.stream()
                .filter(e -> e.getStatus() == WaitingListStatus.NOT_SELECTED)
                .collect(Collectors.toList());
        
        if (waitlisted.isEmpty()) {
            toast("No waitlisted entrants available for replacement");
            return;
        }

        Collections.shuffle(waitlisted);
        int toRedraw = Math.min(selectedUids.size(), waitlisted.size());
        
        for (int i = 0; i < toRedraw; i++) {
            WaitingListEntry entry = waitlisted.get(i);
            entry.setStatus(WaitingListStatus.INVITED);
            waitingListService.updateEntry(eventId, entry, unused -\u003e {}, e -\u003e {});
        }
        
        toast("Redraw completed for " + toRedraw + " entrants");
        refreshEntries();
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private static class EntrantDisplayModel {
        WaitingListEntry entry;
        String name = "Loading...";
        EntrantDisplayModel(WaitingListEntry entry) { this.entry = entry; }
    }

    private class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.ViewHolder> {
        private final List<EntrantDisplayModel> list;
        private final Set<String> selectedUids = new HashSet<>();

        EntrantAdapter(List<EntrantDisplayModel> list) { this.list = list; }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_organizer_entrant, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EntrantDisplayModel model = list.get(position);
            holder.tvName.setText(model.name);
            
            boolean isDeclinedView = cgStatus.getCheckedChipId() == R.id.chip_declined;
            holder.checkbox.setVisibility(isDeclinedView ? View.VISIBLE : View.GONE);
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(selectedUids.contains(model.entry.getEntrantUid()));
            holder.checkbox.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) selectedUids.add(model.entry.getEntrantUid());
                else selectedUids.remove(model.entry.getEntrantUid());
            });

            boolean isInvitedView = cgStatus.getCheckedChipId() == R.id.chip_invited;
            if (isInvitedView) {
                holder.ivStatus.setVisibility(View.VISIBLE);
                if (model.entry.getStatus() == WaitingListStatus.INVITED) {
                    holder.ivStatus.setImageResource(android.R.drawable.ic_menu_recent_history); // Clock
                } else if (model.entry.getStatus() == WaitingListStatus.ACCEPTED) {
                    holder.ivStatus.setImageResource(android.R.drawable.checkbox_on_background); // Check
                }
            } else {
                holder.ivStatus.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);

            if (isInvitedView && removeEntrantMode) {
                holder.itemView.setClickable(true);
                holder.itemView.setOnClickListener(v -> removeEntrant(model));
            }
        }

        @Override public int getItemCount() { return list.size(); }

        public Set<String> getSelectedUids() { return selectedUids; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            CheckBox checkbox;
            ImageView ivStatus;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_entrant_name);
                checkbox = v.findViewById(R.id.checkbox_entrant);
                ivStatus = v.findViewById(R.id.iv_status_indicator);
            }
        }
    }
}
