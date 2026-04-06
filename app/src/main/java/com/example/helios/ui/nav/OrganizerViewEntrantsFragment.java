package com.example.helios.ui.nav;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.EventService;
import com.example.helios.service.LotteryService;
import com.example.helios.service.OrganizerNotificationService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.event.EventUiFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private EventService eventService;
    private LotteryService lotteryService;
    private WaitingListService waitingListService;
    private ProfileService profileService;

    private final List<WaitingListEntry> allEntries = new ArrayList<>();
    private final List<EntrantDisplayModel> displayList = new ArrayList<>();
    private final Map<String, String> entrantNames = new HashMap<>();
    private final Map<String, String> entrantImageUrls = new HashMap<>();
    private EntrantAdapter adapter;
    private boolean removeEntrantMode = false;

    private TextView tvDrawIndicator, tvListHeader, tvInfoStats, tvHeaderTitle, tvHeaderSubtitle;
    private View llFilters, layoutDrawControls, layoutDeclinedActions, layoutInvitedActions;
    private View layoutDrawControlsHeader, layoutDrawControlsInput;
    private TextView tvDrawControlsHelper;
    private MaterialButton btnDraw, btnRemoveEntrant, btnToggleDrawControls;
    private EditText etSearch, etDrawCount;
    private boolean drawControlsExpanded = false;

    private String pendingCsvContent;
    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri != null && pendingCsvContent != null) {
                    writeCsvToUri(uri, pendingCsvContent);
                }
                pendingCsvContent = null;
            });

    private ChipGroup cgStatus;
    private OrganizerNotificationService organizerNotificationService;

    /**
     * Default constructor for OrganizerViewEntrantsFragment.
     */
    public OrganizerViewEntrantsFragment() {
        super(R.layout.fragment_organizer_view_entrants);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        lotteryService = application.getLotteryService();
        waitingListService = application.getWaitingListService();
        profileService = application.getProfileService();
        organizerNotificationService = application.getOrganizerNotificationService();
        eventId = EventNavArgs.getEventId(getArguments());
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
        tvHeaderTitle = view.findViewById(R.id.submenu_title);
        tvHeaderSubtitle = view.findViewById(R.id.submenu_subtitle);
        tvDrawIndicator = view.findViewById(R.id.tv_draw_indicator);
        tvListHeader = view.findViewById(R.id.tv_list_header);
        tvInfoStats = view.findViewById(R.id.tv_info_stats);
        llFilters = view.findViewById(R.id.ll_filters);
        layoutDrawControls = view.findViewById(R.id.layout_draw_controls);
        layoutDeclinedActions = view.findViewById(R.id.layout_declined_actions);
        layoutInvitedActions = view.findViewById(R.id.layout_invited_actions);
        layoutDrawControlsHeader = view.findViewById(R.id.layout_draw_controls_header);
        layoutDrawControlsInput = view.findViewById(R.id.layout_draw_controls_input);
        tvDrawControlsHelper = view.findViewById(R.id.tv_draw_controls_helper);
        btnToggleDrawControls = view.findViewById(R.id.btn_toggle_draw_controls);
        etSearch = view.findViewById(R.id.et_search);
        etDrawCount = view.findViewById(R.id.et_draw_count);
        btnDraw = view.findViewById(R.id.btn_draw);
        btnRemoveEntrant = view.findViewById(R.id.btn_remove_entrant);
        cgStatus = view.findViewById(R.id.cg_status);

        tvHeaderTitle.setText("Manage Entrants");
        tvHeaderSubtitle.setText("Review invited entrants, the replacement pool, and cancelled responses.");
        tvHeaderSubtitle.setSingleLine(true);
        tvHeaderSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        view.findViewById(R.id.submenu_back_button).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

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
        view.findViewById(R.id.btn_export).setOnClickListener(v -> exportFinalList());
        btnToggleDrawControls.setOnClickListener(v -> toggleDrawControls());
        layoutDrawControlsHeader.setOnClickListener(v -> toggleDrawControls());
    }

    private void toggleDrawControls() {
        drawControlsExpanded = !drawControlsExpanded;
        tvDrawControlsHelper.setVisibility(drawControlsExpanded ? View.VISIBLE : View.GONE);
        layoutDrawControlsInput.setVisibility(drawControlsExpanded ? View.VISIBLE : View.GONE);
        btnToggleDrawControls.setIconResource(drawControlsExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    /**
     * Loads event and entrant data from services.
     */
    private void loadData() {
        if (eventId == null) return;

        eventService.getEventById(eventId, loadedEvent -> {
            this.event = loadedEvent;
            if (loadedEvent != null) {
                tvHeaderSubtitle.setText(
                        EventUiFormatter.getTitle(loadedEvent)
                                + " | "
                                + EventUiFormatter.getScheduleLabel(loadedEvent)
                );
            }
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
            prefetchEntrantNames(entries);
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
        boolean statusViewEnabled = isStatusViewEnabled();
        tvDrawIndicator.setVisibility(drawn ? View.VISIBLE : View.GONE);
        tvDrawIndicator.setText(drawn ? "Draw completed" : "Draw pending");
        llFilters.setVisibility(statusViewEnabled ? View.VISIBLE : View.GONE);
        tvListHeader.setText(statusViewEnabled ? "Selected" : "Waiting");
        layoutDrawControls.setVisibility(View.VISIBLE);
        btnDraw.setText(drawn ? "Update Draw" : "Run Draw");

        if (statusViewEnabled && cgStatus.getCheckedChipId() == View.NO_ID) {
            cgStatus.check(getDefaultStatusChipId());
        }
    }

    /**
     * Filters and updates the displayed entrant list based on search query and selected status filter.
     */
    private void updateDisplayList() {
        displayList.clear();
        String query = etSearch.getText().toString().toLowerCase(Locale.CANADA);
        boolean statusViewEnabled = isStatusViewEnabled();
        int checkedId = cgStatus.getCheckedChipId();
        final int selectedChipId = (statusViewEnabled && checkedId == View.NO_ID)
                ? getDefaultStatusChipId()
                : checkedId;
        boolean invitedFilter = statusViewEnabled && selectedChipId == R.id.chip_invited;

        List<WaitingListEntry> filtered = allEntries.stream().filter(entry -> {
            if (!statusViewEnabled) return entry.getStatus() == WaitingListStatus.WAITING;

            if (selectedChipId == R.id.chip_invited) 
                return entry.getStatus() == WaitingListStatus.INVITED || entry.getStatus() == WaitingListStatus.ACCEPTED;
            if (selectedChipId == R.id.chip_waitlisted)
                return entry.getStatus() == WaitingListStatus.NOT_SELECTED
                        || entry.getStatus() == WaitingListStatus.WAITING;
            if (selectedChipId == R.id.chip_declined)
                return entry.getStatus() == WaitingListStatus.DECLINED || entry.getStatus() == WaitingListStatus.CANCELLED;

            return false;
        }).filter(entry -> matchesSearch(entry, query)).collect(Collectors.toList());

        updateHeaderForSelection(statusViewEnabled, selectedChipId);

        layoutDeclinedActions.setVisibility(
                statusViewEnabled && selectedChipId == R.id.chip_declined ? View.VISIBLE : View.GONE
        );
        layoutInvitedActions.setVisibility(
                statusViewEnabled && selectedChipId == R.id.chip_invited ? View.VISIBLE : View.GONE
        );
        btnRemoveEntrant.setVisibility(invitedFilter ? View.VISIBLE : View.GONE);
        if (!invitedFilter && removeEntrantMode) {
            removeEntrantMode = false;
        }
        updateRemoveEntrantButtonState();

        if (invitedFilter) {
            long confirmed = filtered.stream().filter(e -> e.getStatus() == WaitingListStatus.ACCEPTED).count();
            tvInfoStats.setText(confirmed + "/" + filtered.size() + " accepted");
        } else if (statusViewEnabled && selectedChipId == R.id.chip_waitlisted) {
            tvInfoStats.setText(filtered.size() + " in pool");
        } else if (statusViewEnabled && selectedChipId == R.id.chip_declined) {
            tvInfoStats.setText(filtered.size() + " cancelled");
        } else {
            tvInfoStats.setText(filtered.size() + " waiting");
        }
        tvInfoStats.setVisibility(View.VISIBLE);

        for (WaitingListEntry entry : filtered) {
            displayList.add(new EntrantDisplayModel(
                    entry,
                    getDisplayName(entry),
                    getProfileImageUrl(entry)
            ));
        }
        adapter.replaceEntries(displayList);
    }

    private void prefetchEntrantNames(@NonNull List<WaitingListEntry> entries) {
        for (WaitingListEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String entrantUid = entry.getEntrantUid();
            if (entrantUid == null || entrantUid.trim().isEmpty() || entrantNames.containsKey(entrantUid)) {
                continue;
            }
            profileService.getUserProfile(entrantUid, profile -> {
                String resolvedName = profile != null
                        && profile.getName() != null
                        && !profile.getName().trim().isEmpty()
                        ? profile.getName().trim()
                        : "Unknown Entrant";
                entrantNames.put(entrantUid, resolvedName);
                entrantImageUrls.put(entrantUid, normalize(profile != null ? profile.getProfileImageUrl() : null));
                if (isAdded()) {
                    updateDisplayList();
                }
            }, e -> {
                entrantNames.put(entrantUid, "Unknown Entrant");
                entrantImageUrls.put(entrantUid, null);
                if (isAdded()) {
                    updateDisplayList();
                }
            });
        }
    }

    @NonNull
    private String getDisplayName(@NonNull WaitingListEntry entry) {
        String entrantUid = entry.getEntrantUid();
        if (entrantUid == null || entrantUid.trim().isEmpty()) {
            return "Unknown Entrant";
        }
        String cachedName = entrantNames.get(entrantUid);
        return cachedName != null ? cachedName : "Loading...";
    }

    @Nullable
    private String getProfileImageUrl(@NonNull WaitingListEntry entry) {
        String entrantUid = entry.getEntrantUid();
        if (!hasText(entrantUid)) {
            return null;
        }
        return entrantImageUrls.get(entrantUid);
    }

    private boolean matchesSearch(@NonNull WaitingListEntry entry, @NonNull String query) {
        if (query.isEmpty()) {
            return true;
        }
        String name = getDisplayName(entry).toLowerCase(Locale.CANADA);
        String uid = entry.getEntrantUid() == null ? "" : entry.getEntrantUid().toLowerCase(Locale.CANADA);
        return name.contains(query) || uid.contains(query);
    }

    private void updateHeaderForSelection(boolean statusViewEnabled, int selectedChipId) {
        if (!statusViewEnabled) {
            tvListHeader.setText("Waiting");
            return;
        }
        if (selectedChipId == R.id.chip_invited) {
            tvListHeader.setText("Selected");
        } else if (selectedChipId == R.id.chip_waitlisted) {
            tvListHeader.setText("Pool");
        } else if (selectedChipId == R.id.chip_declined) {
            tvListHeader.setText("Cancelled");
        } else {
            tvListHeader.setText("Entrants");
        }
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
        long now = System.currentTimeMillis();
        for (int i = 0; i < toInvite; i++) {
            WaitingListEntry entry = waitlisted.get(i);
            entry.setStatus(WaitingListStatus.INVITED);
            entry.setInvitedAtMillis(now);
            entry.setStatusReason("Selected in organizer draw update");
            updates.add(entry);
        }

        profileService.ensureSignedIn(firebaseUser ->
                        updateEntries(updates, () -> notifyReplacementInvites(
                                firebaseUser.getUid(),
                                updates,
                                toInvite,
                                additionalNeeded,
                                "Draw updated"
                        )),
                error -> toast("Auth failed: " + error.getMessage())
        );
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
            entry.setStatusReason("Moved back to replacement pool by organizer");
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

    private boolean isStatusViewEnabled() {
        return event != null && (event.isDrawHappened() || event.isPrivateEvent());
    }

    private int getDefaultStatusChipId() {
        return event != null && event.isPrivateEvent()
                ? R.id.chip_invited
                : R.id.chip_waitlisted;
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
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        if (removeEntrantMode) {
            toast("Tap an entrant to remove them from the list");
        }
    }

    /**
     * Updates the text of the remove entrant toggle button.
     */
    private void updateRemoveEntrantButtonState() {
        if (btnRemoveEntrant == null) return;
        btnRemoveEntrant.setText(removeEntrantMode ? "Done" : "Remove Entrants");
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
        Set<String> selectedUids = new HashSet<>(adapter.getSelectedUids());
        if (selectedUids.isEmpty()) {
            toast("No entrants selected");
            return;
        }
        AtomicInteger pending = new AtomicInteger(selectedUids.size());
        AtomicBoolean hadFailure = new AtomicBoolean(false);
        for (String uid : selectedUids) {
            waitingListService.removeEntry(eventId, uid, unused -> {
                if (pending.decrementAndGet() == 0) {
                    toast(hadFailure.get() ? "Deleted selected entrants with some failures" : "Deleted selected entrants");
                    refreshEntries();
                }
            }, e -> {
                hadFailure.set(true);
                if (pending.decrementAndGet() == 0) {
                    toast("Deleted selected entrants with some failures");
                    refreshEntries();
                }
            });
        }
    }

    /**
     * Replaces selected declined/cancelled entrants with new ones drawn randomly from the waitlist.
     */
    private void redrawReplacements() {
        Set<String> selectedUids = new HashSet<>(adapter.getSelectedUids());
        if (selectedUids.isEmpty()) {
            toast("No entrants selected");
            return;
        }

        if (event == null) {
            toast("Event not loaded");
            return;
        }

        List<WaitingListEntry> waitlisted = allEntries.stream()
                .filter(this::isWaitlistStatus)
                .filter(e -> e.getEntrantUid() != null && !selectedUids.contains(e.getEntrantUid()))
                .collect(Collectors.toList());

        if (waitlisted.isEmpty()) {
            toast("No entrants are available in the replacement pool");
            return;
        }

        Collections.shuffle(waitlisted);
        int toRedraw = Math.min(selectedUids.size(), waitlisted.size());
        List<WaitingListEntry> replacements = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < toRedraw; i++) {
            WaitingListEntry entry = waitlisted.get(i);
            entry.setStatus(WaitingListStatus.INVITED);
            entry.setInvitedAtMillis(now);
            entry.setStatusReason("Replacement draw after cancellation or decline");
            replacements.add(entry);
        }

        profileService.ensureSignedIn(firebaseUser ->
                        updateEntries(replacements, () -> notifyReplacementInvites(
                                firebaseUser.getUid(),
                                replacements,
                                toRedraw,
                                selectedUids.size(),
                                "Replacement draw completed"
                        )),
                error -> toast("Auth failed: " + error.getMessage())
        );
    }

    private void exportFinalList() {
        List<EntrantDisplayModel> accepted = new ArrayList<>();
        for (EntrantDisplayModel model : displayList) {
            WaitingListEntry e = model.entry;
            if (e != null && (e.getStatus() == WaitingListStatus.ACCEPTED
                    || e.getStatus() == WaitingListStatus.INVITED)) {
                accepted.add(model);
            }
        }
        if (accepted.isEmpty()) {
            // fall back to all accepted across all entries, not just current filter
            for (EntrantDisplayModel model : new ArrayList<>(allEntries.stream()
                    .filter(e -> e != null && (e.getStatus() == WaitingListStatus.ACCEPTED
                            || e.getStatus() == WaitingListStatus.INVITED))
                    .map(e -> new EntrantDisplayModel(
                            e,
                            getDisplayName(e),
                            getProfileImageUrl(e)
                    ))
                    .collect(Collectors.toList()))) {
                accepted.add(model);
            }
        }

        if (accepted.isEmpty()) {
            toast("No accepted entrants to export yet");
            return;
        }

        StringBuilder csv = new StringBuilder("Name,Status,UID\n");
        for (EntrantDisplayModel model : accepted) {
            String name = model.name != null ? model.name.replace(",", " ") : "Unknown";
            String status = model.entry.getStatus() != null ? model.entry.getStatus().name() : "";
            String uid = model.entry.getEntrantUid() != null ? model.entry.getEntrantUid() : "";
            csv.append('"').append(name).append('"').append(',')
               .append(status).append(',')
               .append(uid).append('\n');
        }

        try {
            String eventTitle = event != null && event.getTitle() != null
                    ? event.getTitle().replaceAll("[^a-zA-Z0-9]", "_") : "event";
            String filename = "enrolled_" + eventTitle + ".csv";

            pendingCsvContent = csv.toString();
            createDocumentLauncher.launch(filename);

        } catch (Exception e) {
            toast("Export setup failed: " + e.getMessage());
        }
    }

    private void writeCsvToUri(@NonNull Uri uri, @NonNull String content) {
        try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
            if (os != null) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
                toast("CSV saved successfully");
            }
        } catch (IOException e) {
            toast("Failed to save file: " + e.getMessage());
        }
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void notifyReplacementInvites(
            @NonNull String organizerUid,
            @NonNull List<WaitingListEntry> replacements,
            int invitedCount,
            int requestedCount,
            @NonNull String actionLabel
    ) {
        if (event == null || replacements.isEmpty()) {
            finishReplacementInviteFlow(actionLabel, invitedCount, requestedCount, false);
            return;
        }

        AtomicInteger pending = new AtomicInteger(replacements.size());
        AtomicBoolean hadFailure = new AtomicBoolean(false);
        for (WaitingListEntry replacement : replacements) {
            organizerNotificationService.notifyReplacementInvite(
                    organizerUid,
                    event,
                    replacement.getEntrantUid(),
                    result -> {
                        if (pending.decrementAndGet() == 0) {
                            finishReplacementInviteFlow(actionLabel, invitedCount, requestedCount, hadFailure.get());
                        }
                    },
                    error -> {
                        hadFailure.set(true);
                        if (pending.decrementAndGet() == 0) {
                            finishReplacementInviteFlow(actionLabel, invitedCount, requestedCount, true);
                        }
                    }
            );
        }
    }

    private void finishReplacementInviteFlow(
            @NonNull String actionLabel,
            int invitedCount,
            int requestedCount,
            boolean notificationFailure
    ) {
        StringBuilder message = new StringBuilder(actionLabel);
        message.append(". Invited ");
        message.append(invitedCount);
        message.append(invitedCount == 1 ? " entrant" : " entrants");
        if (requestedCount > invitedCount) {
            message.append(". Only ");
            message.append(invitedCount);
            message.append(" of ");
            message.append(requestedCount);
            message.append(" requested spots were available");
        }
        if (notificationFailure) {
            message.append(". Some notifications failed");
        }
        toast(message.toString());
        refreshEntries();
    }

    @NonNull
    private String buildEntryMeta(@NonNull WaitingListEntry entry) {
        String reason = entry.getStatusReason();
        if (reason != null && !reason.trim().isEmpty()) {
            return reason.trim();
        }
        switch (entry.getStatus()) {
            case ACCEPTED:
                return "Accepted invitation";
            case INVITED:
                return "Invitation sent";
            case WAITING:
                return "Waiting for the first draw";
            case NOT_SELECTED:
                return "Available for replacement draw";
            case DECLINED:
                return "Declined invitation";
            case CANCELLED:
                return "Cancelled or removed";
            default:
                return "";
        }
    }

    @Nullable
    private String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    @NonNull
    private String getStatusLabel(@NonNull WaitingListEntry entry) {
        switch (entry.getStatus()) {
            case ACCEPTED:
                return "Accepted";
            case INVITED:
                return "Invited";
            case WAITING:
                return "Waiting";
            case NOT_SELECTED:
                return "Pool";
            case DECLINED:
                return "Declined";
            case CANCELLED:
                return "Cancelled";
            default:
                return "Entrant";
        }
    }

    /**
     * Display model that combines a waiting list entry with a user's name.
     */
    private static class EntrantDisplayModel {
        WaitingListEntry entry;
        String name;
        @Nullable String profileImageUrl;

        EntrantDisplayModel(
                @NonNull WaitingListEntry entry,
                @NonNull String name,
                @Nullable String profileImageUrl
        ) {
            this.entry = entry;
            this.name = name;
            this.profileImageUrl = profileImageUrl;
        }
    }

    /**
     * RecyclerView adapter for the entrant list.
     */
    private class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.ViewHolder> {
        private final List<EntrantDisplayModel> list = new ArrayList<>();
        private final Set<String> selectedUids = new HashSet<>();

        EntrantAdapter(List<EntrantDisplayModel> list) {
            replaceEntries(list);
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_organizer_entrant, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EntrantDisplayModel model = list.get(position);
            holder.tvName.setText(model.name);
            holder.tvMeta.setText(buildEntryMeta(model.entry));
            holder.tvMeta.setVisibility(holder.tvMeta.getText().length() > 0 ? View.VISIBLE : View.GONE);
            holder.tvStatus.setText(getStatusLabel(model.entry));
            bindEntrantAvatar(holder.ivAvatar, model.profileImageUrl);

            boolean isDeclinedView = cgStatus.getCheckedChipId() == R.id.chip_declined;
            holder.checkbox.setVisibility(isDeclinedView ? View.VISIBLE : View.GONE);
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(selectedUids.contains(model.entry.getEntrantUid()));
            holder.checkbox.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) selectedUids.add(model.entry.getEntrantUid());
                else selectedUids.remove(model.entry.getEntrantUid());
            });

            boolean isInvitedView = cgStatus.getCheckedChipId() == R.id.chip_invited;
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            holder.itemView.setAlpha(1f);

            if (isInvitedView && removeEntrantMode) {
                holder.itemView.setClickable(true);
                holder.itemView.setAlpha(0.82f);
                holder.itemView.setOnClickListener(v -> removeEntrant(model));
            }
        }

        @Override public int getItemCount() { return list.size(); }

        void replaceEntries(@NonNull List<EntrantDisplayModel> updatedEntries) {
            List<EntrantDisplayModel> previous = new ArrayList<>(list);
            List<EntrantDisplayModel> next = new ArrayList<>(updatedEntries);
            Set<String> visibleUids = next.stream()
                    .map(model -> model.entry.getEntrantUid())
                    .filter(uid -> uid != null && !uid.trim().isEmpty())
                    .collect(Collectors.toSet());
            selectedUids.retainAll(visibleUids);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new EntrantDiff(previous, next));
            list.clear();
            list.addAll(next);
            diffResult.dispatchUpdatesTo(this);
        }

        /**
         * @return The set of UIDs of entrants currently selected via checkboxes.
         */
        public Set<String> getSelectedUids() { return selectedUids; }

        /**
         * ViewHolder for entrant items.
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            ShapeableImageView ivAvatar;
            TextView tvName;
            TextView tvMeta;
            TextView tvStatus;
            CheckBox checkbox;
            ViewHolder(View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.iv_entrant_icon);
                tvName = v.findViewById(R.id.tv_entrant_name);
                tvMeta = v.findViewById(R.id.tv_entrant_meta);
                tvStatus = v.findViewById(R.id.tv_status_indicator);
                checkbox = v.findViewById(R.id.checkbox_entrant);
            }
        }

        private final class EntrantDiff extends DiffUtil.Callback {
            private final List<EntrantDisplayModel> oldItems;
            private final List<EntrantDisplayModel> newItems;

            private EntrantDiff(
                    @NonNull List<EntrantDisplayModel> oldItems,
                    @NonNull List<EntrantDisplayModel> newItems
            ) {
                this.oldItems = oldItems;
                this.newItems = newItems;
            }

            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldUid = oldItems.get(oldItemPosition).entry.getEntrantUid();
                String newUid = newItems.get(newItemPosition).entry.getEntrantUid();
                return oldUid != null && oldUid.equals(newUid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                EntrantDisplayModel oldItem = oldItems.get(oldItemPosition);
                EntrantDisplayModel newItem = newItems.get(newItemPosition);
                return equalsNullable(oldItem.name, newItem.name)
                        && equalsNullable(oldItem.profileImageUrl, newItem.profileImageUrl)
                        && oldItem.entry.getStatus() == newItem.entry.getStatus()
                        && equalsNullable(oldItem.entry.getStatusReason(), newItem.entry.getStatusReason());
            }

            private boolean equalsNullable(@Nullable String left, @Nullable String right) {
                return left == null ? right == null : left.equals(right);
            }
        }
    }

    private void bindEntrantAvatar(
            @NonNull ShapeableImageView imageView,
            @Nullable String imageUrl
    ) {
        Glide.with(imageView).clear(imageView);
        imageView.setImageTintList(null);
        if (!hasText(imageUrl)) {
            imageView.setImageResource(R.drawable.ic_avatar_person_placeholder);
            return;
        }
        Glide.with(imageView)
                .load(imageUrl)
                .placeholder(R.drawable.ic_avatar_person_placeholder)
                .error(R.drawable.ic_avatar_person_placeholder)
                .circleCrop()
                .into(imageView);
    }
}
