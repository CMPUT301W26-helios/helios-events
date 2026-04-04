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
import com.example.helios.service.LotteryService;
import com.example.helios.service.OrganizerNotificationService;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Fragment that allows an organizer to view and manage the list of entrants for an event.
 * Handles the lottery draw, redraws, and removing entrants from various statuses (invited, accepted, declined).
 */
public class OrganizerViewEntrantsFragment extends Fragment {
    private static final String NO_PEOPLE_IN_EVENT_MESSAGE = "There are no people in this event";

    private String eventId;
    private Event event;
    private final EventService eventService = new EventService();
    private final LotteryService lotteryService = new LotteryService();
    private final WaitingListService waitingListService = new WaitingListService();
    private final ProfileService profileService = new ProfileService();

    private final List<WaitingListEntry> allEntries = new ArrayList<>();
    private final List<EntrantDisplayModel> displayList = new ArrayList<>();
    private EntrantAdapter adapter;
    private boolean removeEntrantMode = false;

    private TextView tvDrawIndicator, tvListHeader, tvInfoStats;
    private View llFilters, layoutDrawControls, layoutDeclinedActions, layoutInvitedActions;
    private EditText etSearch;
    private EditText etDrawCount;
    private MaterialButton btnRemoveEntrant;
    private ChipGroup cgStatus;
    private final OrganizerNotificationService organizerNotificationService =
            new OrganizerNotificationService();

    /**
     * Default constructor for OrganizerViewEntrantsFragment.
     */
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

    /**
     * Initializes UI references from the layout.
     *
     * @param view The fragment's root view.
     */
    private void initViews(View view) {
        tvDrawIndicator = view.findViewById(R.id.tv_draw_indicator);
        tvListHeader = view.findViewById(R.id.tv_list_header);
        tvInfoStats = view.findViewById(R.id.tv_info_stats);
        llFilters = view.findViewById(R.id.ll_filters);
        layoutDrawControls = view.findViewById(R.id.layout_draw_controls);
        layoutDeclinedActions = view.findViewById(R.id.layout_declined_actions);
        layoutInvitedActions = view.findViewById(R.id.layout_invited_actions);
        etSearch = view.findViewById(R.id.et_search);
        etDrawCount = view.findViewById(R.id.et_draw_count);
        btnRemoveEntrant = view.findViewById(R.id.btn_remove_entrant);
        cgStatus = view.findViewById(R.id.cg_status);
        updateRemoveEntrantButtonState();
    }

    /**
     * Sets up the RecyclerView for displaying the entrant list.
     *
     * @param view The fragment's root view.
     */
    private void setupRecyclerView(View view) {
        RecyclerView rv = view.findViewById(R.id.rv_entrants);
        adapter = new EntrantAdapter(displayList);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
    }

    /**
     * Sets up listeners for various UI interactions (back button, search, filters, actions).
     *
     * @param view The fragment's root view.
     */
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

    /**
     * Loads event and entrant data from services.
     */
    private void loadData() {
        if (eventId == null) return;

        eventService.getEventById(eventId, loadedEvent -> {
            this.event = loadedEvent;
            refreshEntries();
        }, e -> toast("Failed to load event"));
    }

    /**
     * Refreshes the entrant list for the current event.
     */
    private void refreshEntries() {
        waitingListService.getEntriesForEvent(eventId, entries -> {
            allEntries.clear();
            allEntries.addAll(entries);
            updateDrawCountDisplay();
            updateUiState();
            updateDisplayList();
        }, e -> toast("Failed to load entrants"));
    }

    /**
     * Updates the general UI state based on whether the draw has occurred.
     */
    private void updateUiState() {
        boolean drawn = event != null && event.isDrawHappened();
        tvDrawIndicator.setVisibility(drawn ? View.VISIBLE : View.GONE);
        llFilters.setVisibility(drawn ? View.VISIBLE : View.GONE);
        tvListHeader.setText(drawn ? "Entrant Status" : "Waiting List");
        layoutDrawControls.setVisibility(View.VISIBLE);
        layoutInvitedActions.setVisibility(View.GONE);
        
        if (drawn && cgStatus.getCheckedChipId() == View.NO_ID) {
            cgStatus.check(R.id.chip_waitlisted);
        }
    }

    /**
     * Filters and updates the displayed entrant list based on search query and selected status filter.
     */
    private void updateDisplayList() {
        displayList.clear();
        String query = etSearch.getText().toString().toLowerCase(Locale.CANADA);
        boolean drawn = event != null && event.isDrawHappened();
        int checkedId = cgStatus.getCheckedChipId();
        final int selectedChipId = (drawn && checkedId == View.NO_ID)
                ? R.id.chip_waitlisted
                : checkedId;
        boolean invitedFilter = drawn && selectedChipId == R.id.chip_invited;

        List<WaitingListEntry> filtered = allEntries.stream().filter(entry -> {
            if (!drawn) return entry.getStatus() == WaitingListStatus.WAITING;
            
            if (selectedChipId == R.id.chip_invited) 
                return entry.getStatus() == WaitingListStatus.INVITED || entry.getStatus() == WaitingListStatus.ACCEPTED;
            if (selectedChipId == R.id.chip_waitlisted)
                return entry.getStatus() == WaitingListStatus.NOT_SELECTED
                        || entry.getStatus() == WaitingListStatus.WAITING;
            if (selectedChipId == R.id.chip_declined)
                return entry.getStatus() == WaitingListStatus.DECLINED || entry.getStatus() == WaitingListStatus.CANCELLED;
            
            return false;
        }).collect(Collectors.toList());

        layoutDeclinedActions.setVisibility(drawn && selectedChipId == R.id.chip_declined ? View.VISIBLE : View.GONE);
        layoutInvitedActions.setVisibility(drawn && selectedChipId == R.id.chip_invited ? View.VISIBLE : View.GONE);
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
        } else if (drawn && selectedChipId == R.id.chip_waitlisted) {
            tvInfoStats.setText(filtered.size() + " people");
            tvInfoStats.setVisibility(View.VISIBLE);
        } else if (drawn && selectedChipId == R.id.chip_declined) {
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
            }, e -> {
                model.name = "Unknown Entrant";
                adapter.notifyDataSetChanged();
            });
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Performs a lottery draw or adjusts the number of invited entrants based on the requested count.
     */
    private void performDraw() {
        if (event == null) {
            toast("Event not loaded");
            return;
        }

        int targetCount = parseRequestedDrawCount();
        if (targetCount < 0) {
            return;
        }

        boolean drawAlreadyHappened = event.isDrawHappened();
        List<WaitingListEntry> selectedEntrants = allEntries.stream()
                .filter(this::isSelectedStatus)
                .collect(Collectors.toList());
        int currentSelected = selectedEntrants.size();

        if (!drawAlreadyHappened) {
            runInitialDraw(targetCount);
            return;
        }

        if (targetCount == currentSelected) {
            toast("Selected entrants already match requested total");
            return;
        }

        if (targetCount > currentSelected) {
            increaseDrawCount(targetCount - currentSelected);
        } else {
            decreaseDrawCount(currentSelected - targetCount, selectedEntrants);
        }
    }

    /**
     * Runs the initial lottery draw, moving entrants from WAITING to INVITED or NOT_SELECTED.
     *
     * @param targetCount The number of entrants to invite.
     */
    private void runInitialDraw(int targetCount) {
        List<WaitingListEntry> candidates = allEntries.stream()
                .filter(e -> e.getStatus() == WaitingListStatus.WAITING)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            toast(NO_PEOPLE_IN_EVENT_MESSAGE);
            return;
        }

        if (targetCount <= 0) {
            toast("Enter at least 1 entrant");
            return;
        }

        int selectedCount = Math.min(targetCount, candidates.size());

        profileService.ensureSignedIn(firebaseUser ->
                        lotteryService.runDraw(
                                firebaseUser.getUid(),
                                event,
                                targetCount,
                                unused -> {
                                    toast("Draw completed! Selected " + selectedCount + " entrants");
                                    refreshEntries();
                                },
                                error -> {
                                    String message = NO_PEOPLE_IN_EVENT_MESSAGE.equals(error.getMessage())
                                            ? NO_PEOPLE_IN_EVENT_MESSAGE
                                            : "Failed to run draw: " + error.getMessage();
                                    toast(message);
                                }
                        ),
                error -> toast("Auth failed: " + error.getMessage())
        );
    }

    /**
     * Invites additional entrants from the waitlist to meet the target count.
     *
     * @param additionalNeeded Number of additional entrants to invite.
     */
    private void increaseDrawCount(int additionalNeeded) {
        List<WaitingListEntry> waitlisted = allEntries.stream()
                .filter(this::isWaitlistStatus)
                .collect(Collectors.toList());

        if (waitlisted.isEmpty()) {
            toast("No waitlisted entrants available");
            return;
        }

        Collections.shuffle(waitlisted);
        int toInvite = Math.min(additionalNeeded, waitlisted.size());
        List<WaitingListEntry> updates = new ArrayList<>();
        for (int i = 0; i < toInvite; i++) {
            WaitingListEntry entry = waitlisted.get(i);
            entry.setStatus(WaitingListStatus.INVITED);
            updates.add(entry);
        }

        updateEntries(updates, () -> {
            if (toInvite < additionalNeeded) {
                toast("Only " + toInvite + " additional entrants were available");
            } else {
                toast("Draw updated. Added " + toInvite + " entrants");
            }
            refreshEntries();
        });
    }

    /**
     * Moves invited/accepted entrants back to NOT_SELECTED status to reduce the count.
     *
     * @param removeCount      Number of entrants to uninvite.
     * @param selectedEntrants List of currently selected entrants.
     */
    private void decreaseDrawCount(int removeCount, @NonNull List<WaitingListEntry> selectedEntrants) {
        if (removeCount <= 0 || selectedEntrants.isEmpty()) {
            toast("No entrants available to move to waitlist");
            return;
        }

        Collections.shuffle(selectedEntrants);
        int toUninvite = Math.min(removeCount, selectedEntrants.size());
        List<WaitingListEntry> updates = new ArrayList<>();
        for (int i = 0; i < toUninvite; i++) {
            WaitingListEntry entry = selectedEntrants.get(i);
            entry.setStatus(WaitingListStatus.NOT_SELECTED);
            updates.add(entry);
        }

        updateEntries(updates, () -> {
            toast("Draw updated. Moved " + toUninvite + " entrants to waitlist");
            refreshEntries();
        });
    }

    /**
     * Parses the requested draw count from the input field.
     *
     * @return The target count, or -1 if the input is invalid.
     */
    private int parseRequestedDrawCount() {
        String raw = etDrawCount.getText() == null ? "" : etDrawCount.getText().toString().trim();
        if (raw.isEmpty()) {
            toast("Enter the total number of selected entrants");
            return -1;
        }

        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                toast("Total selected entrants must be 0 or greater");
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            toast("Enter a valid number");
            return -1;
        }
    }

    /**
     * Checks if an entry's status is either INVITED or ACCEPTED.
     */
    private boolean isSelectedStatus(@NonNull WaitingListEntry entry) {
        return entry.getStatus() == WaitingListStatus.INVITED
                || entry.getStatus() == WaitingListStatus.ACCEPTED;
    }

    /**
     * Checks if an entry's status is either WAITING or NOT_SELECTED.
     */
    private boolean isWaitlistStatus(@NonNull WaitingListEntry entry) {
        return entry.getStatus() == WaitingListStatus.WAITING
                || entry.getStatus() == WaitingListStatus.NOT_SELECTED;
    }

    /**
     * Updates multiple waiting list entries in Firestore sequentially.
     *
     * @param entriesToUpdate List of entries to update.
     * @param onComplete      Runnable to execute after all updates are finished.
     */
    private void updateEntries(@NonNull List<WaitingListEntry> entriesToUpdate, @NonNull Runnable onComplete) {
        if (entriesToUpdate.isEmpty()) {
            onComplete.run();
            return;
        }

        AtomicInteger pending = new AtomicInteger(entriesToUpdate.size());
        AtomicBoolean hadFailure = new AtomicBoolean(false);

        for (WaitingListEntry entry : entriesToUpdate) {
            waitingListService.updateEntry(eventId, entry, unused -> {
                if (pending.decrementAndGet() == 0) {
                    if (hadFailure.get()) toast("Some entrant updates failed");
                    onComplete.run();
                }
            }, e -> {
                hadFailure.set(true);
                if (pending.decrementAndGet() == 0) {
                    toast("Some entrant updates failed");
                    onComplete.run();
                }
            });
        }
    }

    /**
     * Toggles the mode for removing specific entrants from the invited/accepted list.
     */
    private void toggleRemoveEntrantMode() {
        removeEntrantMode = !removeEntrantMode;
        updateRemoveEntrantButtonState();
        adapter.notifyDataSetChanged();
    }

    /**
     * Updates the text of the remove entrant toggle button.
     */
    private void updateRemoveEntrantButtonState() {
        if (btnRemoveEntrant == null) return;
        btnRemoveEntrant.setText(removeEntrantMode ? "Done" : "Remove Entrant");
    }

    /**
     * Removes a single entrant from the event's waiting list.
     *
     * @param model The display model of the entrant to remove.
     */
    private void removeEntrant(@NonNull EntrantDisplayModel model) {
        String entrantUid = model.entry.getEntrantUid();
        if (entrantUid == null || entrantUid.trim().isEmpty()) {
            toast("Unable to remove entrant");
            return;
        }

        if (event == null) {
            toast("Event not loaded");
            return;
        }

        profileService.ensureSignedIn(firebaseUser ->
                        organizerNotificationService.cancelEntrant(
                                firebaseUser.getUid(),
                                event,
                                model.entry,
                                "Removed by organizer",
                                unused -> {
                                    String name = model.name != null ? model.name : "Entrant";
                                    toast(name + " removed");
                                    refreshEntries();
                                },
                                error -> toast("Failed to remove entrant: " + error.getMessage())
                        ),
                error -> toast("Auth failed: " + error.getMessage())
        );
    }

    /**
     * Updates the draw count input field with the current number of selected entrants.
     */
    private void updateDrawCountDisplay() {
        long selectedCount = allEntries.stream()
                .filter(e -> e.getStatus() == WaitingListStatus.INVITED
                        || e.getStatus() == WaitingListStatus.ACCEPTED)
                .count();
        etDrawCount.setText(String.valueOf(selectedCount));
    }

    /**
     * Deletes all entrants currently selected via checkboxes (typically in the Declined tab).
     */
    private void deleteSelectedDeclined() {
        Set<String> selectedUids = adapter.getSelectedUids();
        if (selectedUids.isEmpty()) {
            toast("No entrants selected");
            return;
        }
        for (String uid : selectedUids) {
            waitingListService.removeEntry(eventId, uid, unused -> {}, e -> toast("Failed to delete entrant"));
        }
        toast("Deleted selected entrants");
        refreshEntries();
    }

    /**
     * Replaces selected declined/cancelled entrants with new ones drawn randomly from the waitlist.
     */
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
            waitingListService.updateEntry(eventId, entry, unused -> {}, e -> toast("Failed to redraw replacement"));
        }
        
        toast("Redraw completed for " + toRedraw + " entrants");
        refreshEntries();
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Display model that combines a waiting list entry with a user's name.
     */
    private static class EntrantDisplayModel {
        WaitingListEntry entry;
        String name = "Loading...";
        EntrantDisplayModel(WaitingListEntry entry) { this.entry = entry; }
    }

    /**
     * RecyclerView adapter for the entrant list.
     */
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

        /**
         * @return The set of UIDs of entrants currently selected via checkboxes.
         */
        public Set<String> getSelectedUids() { return selectedUids; }

        /**
         * ViewHolder for entrant items.
         */
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
