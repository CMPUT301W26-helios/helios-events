package com.example.helios.ui.nav;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.helios.model.UserProfile;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reusable user picker for event-scoped actions (e.g., invite to private event, assign co-organizer).
 * This consolidates formerly separate screens that were visually identical.
 */
public class EventUserPickerFragment extends Fragment {
    public enum Mode {
        INVITE_PRIVATE,
        ASSIGN_CO_ORGANIZER
    }

    private static final String ARG_EVENT_ID = "arg_event_id";
    private static final String ARG_MODE = "arg_mode";

    private final EventService eventService = new EventService();
    private final ProfileService profileService = new ProfileService();
    private final WaitingListService waitingListService = new WaitingListService();

    @Nullable private String eventId;
    @Nullable private Mode mode;
    @Nullable private Event loadedEvent;
    @Nullable private String currentUid;

    private final List<UserProfile> allUsers = new ArrayList<>();
    private final List<UserProfile> filteredUsers = new ArrayList<>();

    private EditText searchInput;
    private TextView emptyView;
    private TextView titleView;
    private TextView subtitleView;

    private UserAdapter adapter;

    public EventUserPickerFragment() {
        super(R.layout.fragment_entrant_invite);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString(ARG_EVENT_ID);
            String rawMode = args.getString(ARG_MODE, Mode.INVITE_PRIVATE.name());
            try {
                mode = Mode.valueOf(rawMode);
            } catch (IllegalArgumentException ignored) {
                mode = Mode.INVITE_PRIVATE;
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (eventId == null || eventId.trim().isEmpty()) {
            toast("Missing event id.");
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        titleView = view.findViewById(R.id.tv_user_picker_title);
        subtitleView = view.findViewById(R.id.tv_user_picker_subtitle);
        searchInput = view.findViewById(R.id.et_user_picker_search);
        emptyView = view.findViewById(R.id.tv_user_picker_empty);
        RecyclerView recyclerView = view.findViewById(R.id.rv_user_picker_users);
        MaterialButton doneButton = view.findViewById(R.id.btn_user_picker_done);

        adapter = new UserAdapter(filteredUsers, this::onUserActionPressed);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        doneButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
        });

        bindModeText();
        loadEventAndAuthorize();
    }

    private void bindModeText() {
        if (titleView == null || subtitleView == null || searchInput == null) return;
        if (mode == Mode.ASSIGN_CO_ORGANIZER) {
            titleView.setText("Assign Co-organizer");
            adapter.setActionLabel("Assign");
        } else {
            titleView.setText("Invite Entrants");
            adapter.setActionLabel("Invite");
        }
    }

    private void loadEventAndAuthorize() {
        profileService.ensureSignedIn(firebaseUser -> {
            currentUid = firebaseUser.getUid();
            eventService.getEventById(eventId, event -> {
                if (!isAdded()) return;
                if (event == null) {
                    toast("Event not found.");
                    NavHostFragment.findNavController(this).navigateUp();
                    return;
                }
                loadedEvent = event;

                if (!currentUid.equals(event.getOrganizerUid())) {
                    toast("Only the organizer can perform this action.");
                    NavHostFragment.findNavController(this).navigateUp();
                    return;
                }

                if (mode == Mode.INVITE_PRIVATE && !event.isPrivateEvent()) {
                    toast("Invites are only available for private events.");
                    NavHostFragment.findNavController(this).navigateUp();
                    return;
                }

                loadUsers();
            }, e -> toast("Failed to load event: " + e.getMessage()));
        }, e -> toast("Auth failed: " + e.getMessage()));
    }

    private void loadUsers() {
        profileService.getAllProfiles(users -> {
            if (!isAdded()) return;
            allUsers.clear();

            Set<String> blocked = new HashSet<>();
            if (loadedEvent != null) {
                blocked.add(loadedEvent.getOrganizerUid());
                List<String> co = loadedEvent.getCoOrganizerUids();
                if (co != null) blocked.addAll(co);
            }

            for (UserProfile user : users) {
                if (user == null || user.getUid() == null) continue;
                if (blocked.contains(user.getUid())) continue;
                allUsers.add(user);
            }
            applyFilter(searchInput.getText() == null ? "" : searchInput.getText().toString());
        }, e -> toast("Failed to load users: " + e.getMessage()));
    }

    private void applyFilter(@NonNull String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.CANADA);
        filteredUsers.clear();

        for (UserProfile user : allUsers) {
            String name = safeLower(user.getName());
            String email = safeLower(user.getEmail());
            String phone = safeLower(user.getPhone());
            if (query.isEmpty()
                    || name.contains(query)
                    || email.contains(query)
                    || phone.contains(query)) {
                filteredUsers.add(user);
            }
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onUserActionPressed(@NonNull UserProfile user) {
        if (eventId == null || loadedEvent == null || user.getUid() == null) return;

        if (mode == Mode.ASSIGN_CO_ORGANIZER) {
            assignCoOrganizer(user);
        } else {
            inviteToPrivateEvent(user);
        }
    }

    private void inviteToPrivateEvent(@NonNull UserProfile user) {
        waitingListService.inviteEntrantToWaitingList(
                eventId,
                user.getUid(),
                unused -> toast("Invited " + nonEmptyOr(user.getName(), user.getUid())),
                e -> toast("Invite failed: " + e.getMessage())
        );
    }

    private void assignCoOrganizer(@NonNull UserProfile user) {
        if (loadedEvent == null) return;

        List<String> co = loadedEvent.getCoOrganizerUids();
        if (co == null) co = new ArrayList<>();
        if (co.contains(user.getUid())) {
            toast("Already a co-organizer.");
            return;
        }
        co.add(user.getUid());
        loadedEvent.setCoOrganizerUids(co);

        eventService.saveEvent(loadedEvent, unused -> {
            if (!isAdded()) return;
            waitingListService.removeEntry(eventId, user.getUid(), unused2 -> {
                toast("Assigned co-organizer: " + nonEmptyOr(user.getName(), user.getUid()));
                allUsers.remove(user);
                filteredUsers.remove(user);
                adapter.notifyDataSetChanged();
            }, e -> toast("Assigned co-organizer (could not remove from waiting list): " + e.getMessage()));
        }, e -> toast("Assign failed: " + e.getMessage()));
    }

    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.CANADA);
    }

    @NonNull
    private String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private static class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        interface OnAction {
            void onAction(@NonNull UserProfile user);
        }

        private final List<UserProfile> users;
        private final OnAction onAction;
        private String actionLabel = "Invite";

        UserAdapter(@NonNull List<UserProfile> users, @NonNull OnAction onAction) {
            this.users = users;
            this.onAction = onAction;
        }

        void setActionLabel(@NonNull String label) {
            this.actionLabel = label;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_invite_entrant, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            UserProfile user = users.get(position);
            String name = user.getName() == null || user.getName().trim().isEmpty()
                    ? "(no name)"
                    : user.getName().trim();
            String email = user.getEmail() == null ? "" : user.getEmail().trim();
            String phone = user.getPhone() == null ? "" : user.getPhone().trim();
            String contact = email;
            if (!phone.isEmpty()) {
                contact = contact.isEmpty() ? phone : contact + " | " + phone;
            }
            if (contact.isEmpty()) contact = "(no email/phone)";

            holder.nameView.setText(name);
            holder.contactView.setText(contact);
            holder.actionButton.setText(actionLabel);
            holder.actionButton.setOnClickListener(v -> onAction.onAction(user));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView nameView;
            final TextView contactView;
            final MaterialButton actionButton;

            VH(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.tv_invite_user_name);
                contactView = itemView.findViewById(R.id.tv_invite_user_contact);
                actionButton = itemView.findViewById(R.id.btn_invite_user);
            }
        }
    }
}

