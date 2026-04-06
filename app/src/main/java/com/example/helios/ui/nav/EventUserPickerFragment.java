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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.UserProfile;
import com.example.helios.service.EventService;
import com.example.helios.service.OrganizerNotificationService;
import com.example.helios.service.ProfileService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.HeliosUi;
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

    private enum CoOrganizerActionState {
        INVITE,
        CANCEL,
        REMOVE
    }

    private static final String ARG_MODE = "arg_mode";

    private EventService eventService;
    private OrganizerNotificationService organizerNotificationService;
    private ProfileService profileService;
    private WaitingListService waitingListService;

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
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        organizerNotificationService = application.getOrganizerNotificationService();
        profileService = application.getProfileService();
        waitingListService = application.getWaitingListService();
        Bundle args = getArguments();
        if (args != null) {
            eventId = EventNavArgs.getEventId(args);
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

        MaterialButton backButton = view.findViewById(R.id.submenu_back_button);
        titleView = view.findViewById(R.id.submenu_title);
        subtitleView = view.findViewById(R.id.submenu_subtitle);
        searchInput = view.findViewById(R.id.et_user_picker_search);
        emptyView = view.findViewById(R.id.tv_user_picker_empty);
        RecyclerView recyclerView = view.findViewById(R.id.rv_user_picker_users);
        MaterialButton doneButton = view.findViewById(R.id.btn_user_picker_done);

        adapter = new UserAdapter(
                filteredUsers,
                mode == Mode.ASSIGN_CO_ORGANIZER,
                this::onUserActionPressed,
                this::getCoOrganizerActionState
        );
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
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
            subtitleView.setText("Search by name, email, or phone to manage co-organizer access.");
        } else {
            titleView.setText("Invite Entrants");
            subtitleView.setText("Search by name, email, or phone to invite users to the private event pool.");
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
        adapter.replaceUsers(filteredUsers);
        emptyView.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onUserActionPressed(@NonNull UserProfile user) {
        if (eventId == null || loadedEvent == null || user.getUid() == null) return;

        if (mode == Mode.ASSIGN_CO_ORGANIZER) {
            CoOrganizerActionState actionState = getCoOrganizerActionState(user);
            if (actionState == CoOrganizerActionState.CANCEL) {
                cancelCoOrganizerInvite(user);
            } else if (actionState == CoOrganizerActionState.REMOVE) {
                removeCoOrganizer(user);
            } else {
                inviteCoOrganizer(user);
            }
        } else {
            inviteToPrivateEvent(user);
        }
    }

    private void inviteToPrivateEvent(@NonNull UserProfile user) {
        waitingListService.inviteEntrantToWaitingList(
                eventId,
                user.getUid(),
                unused -> sendPrivateInviteNotification(user),
                e -> toast("Invite failed: " + e.getMessage())
        );
    }

    @NonNull
    private CoOrganizerActionState getCoOrganizerActionState(@NonNull UserProfile user) {
        if (loadedEvent == null || user.getUid() == null) {
            return CoOrganizerActionState.INVITE;
        }
        if (loadedEvent.isCoOrganizer(user.getUid())) {
            return CoOrganizerActionState.REMOVE;
        }
        if (loadedEvent.isPendingCoOrganizer(user.getUid())) {
            return CoOrganizerActionState.CANCEL;
        }
        return CoOrganizerActionState.INVITE;
    }

    private void inviteCoOrganizer(@NonNull UserProfile user) {
        if (loadedEvent == null || user.getUid() == null) return;

        List<String> pending = loadedEvent.getPendingCoOrganizerUids();
        if (pending == null) pending = new ArrayList<>();
        if (pending.contains(user.getUid())) {
            toast("Co-organizer invite already pending.");
            return;
        }
        pending.add(user.getUid());
        loadedEvent.setPendingCoOrganizerUids(pending);

        eventService.saveEvent(loadedEvent, unused -> {
            if (!isAdded()) return;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            sendCoOrganizerInviteNotification(user);
        }, e -> toast("Invite failed: " + e.getMessage()));
    }

    private void sendPrivateInviteNotification(@NonNull UserProfile user) {
        if (loadedEvent == null || currentUid == null || user.getUid() == null) {
            toast("Invited " + nonEmptyOr(user.getName(), user.getUid()));
            return;
        }

        String displayName = nonEmptyOr(user.getName(), user.getUid());
        organizerNotificationService.notifyPrivateEventInvite(
                currentUid,
                loadedEvent,
                user.getUid(),
                result -> toast("Invited " + displayName + "."),
                error -> toast("Invited " + displayName + ", but failed to send notification.")
        );
    }

    private void sendCoOrganizerInviteNotification(@NonNull UserProfile user) {
        if (loadedEvent == null || currentUid == null || user.getUid() == null) {
            toast("Invited " + nonEmptyOr(user.getName(), user.getUid()) + " to co-organize.");
            return;
        }

        String displayName = nonEmptyOr(user.getName(), user.getUid());
        organizerNotificationService.notifyCoOrganizerInvite(
                currentUid,
                loadedEvent,
                user.getUid(),
                result -> toast("Invited " + displayName + " to co-organize."),
                error -> toast("Invited " + displayName + " to co-organize, but failed to send notification.")
        );
    }

    private void cancelCoOrganizerInvite(@NonNull UserProfile user) {
        if (loadedEvent == null || user.getUid() == null) return;

        List<String> pending = loadedEvent.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(user.getUid())) {
            toast("No pending invite to cancel.");
            return;
        }
        loadedEvent.setPendingCoOrganizerUids(pending);

        eventService.saveEvent(loadedEvent, unused -> {
            if (!isAdded()) return;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            toast("Cancelled invite for " + nonEmptyOr(user.getName(), user.getUid()) + ".");
        }, e -> toast("Cancel failed: " + e.getMessage()));
    }

    private void removeCoOrganizer(@NonNull UserProfile user) {
        if (loadedEvent == null || user.getUid() == null) return;

        List<String> coOrganizers = loadedEvent.getCoOrganizerUids();
        if (coOrganizers == null || !coOrganizers.remove(user.getUid())) {
            toast("User is not a co-organizer.");
            return;
        }
        loadedEvent.setCoOrganizerUids(coOrganizers);
        List<String> pending = loadedEvent.getPendingCoOrganizerUids();
        if (pending != null) {
            pending.remove(user.getUid());
            loadedEvent.setPendingCoOrganizerUids(pending);
        }

        eventService.saveEvent(loadedEvent, unused -> {
            if (!isAdded()) return;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            toast("Removed co-organizer: " + nonEmptyOr(user.getName(), user.getUid()));
        }, e -> toast("Remove failed: " + e.getMessage()));
    }

    private String safeLower(@Nullable String value) {
        return HeliosText.safeLower(value);
    }

    @NonNull
    private String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        return HeliosText.nonEmptyOr(value, fallback);
    }

    private void toast(@NonNull String msg) {
        HeliosUi.toast(this, msg);
    }

    private static class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
        interface OnAction {
            void onAction(@NonNull UserProfile user);
        }

        interface CoOrganizerStateResolver {
            @NonNull CoOrganizerActionState resolve(@NonNull UserProfile user);
        }

        private final List<UserProfile> users;
        private final boolean coOrganizerMode;
        private final OnAction onAction;
        private final CoOrganizerStateResolver coOrganizerStateResolver;

        UserAdapter(
                @NonNull List<UserProfile> users,
                boolean coOrganizerMode,
                @NonNull OnAction onAction,
                @NonNull CoOrganizerStateResolver coOrganizerStateResolver
        ) {
            this.users = users;
            this.coOrganizerMode = coOrganizerMode;
            this.onAction = onAction;
            this.coOrganizerStateResolver = coOrganizerStateResolver;
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
            bindActionButton(holder.actionButton, user);
            holder.actionButton.setOnClickListener(v -> onAction.onAction(user));
        }

        private void bindActionButton(@NonNull MaterialButton button, @NonNull UserProfile user) {
            if (!coOrganizerMode) {
                applyInviteButtonStyle(button);
                return;
            }

            CoOrganizerActionState actionState = coOrganizerStateResolver.resolve(user);
            if (actionState == CoOrganizerActionState.INVITE) {
                applyInviteButtonStyle(button);
            } else if (actionState == CoOrganizerActionState.CANCEL) {
                applyDestructiveButtonStyle(button, "Cancel");
            } else {
                applyDestructiveButtonStyle(button, "Remove");
            }
        }

        private void applyInviteButtonStyle(@NonNull MaterialButton button) {
            button.setText("Invite");
            button.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            button.setBackgroundTintList(null);
            button.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    button,
                    com.google.android.material.R.attr.colorOnPrimaryContainer
            ));
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(
                            button,
                            com.google.android.material.R.attr.colorPrimaryContainer
                    )));
        }

        private void applyDestructiveButtonStyle(@NonNull MaterialButton button, @NonNull String label) {
            button.setText(label);
            button.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(
                            button,
                            com.google.android.material.R.attr.colorErrorContainer
                    )));
            button.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    button,
                    com.google.android.material.R.attr.colorOnErrorContainer
            ));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        void replaceUsers(@NonNull List<UserProfile> updatedUsers) {
            List<UserProfile> previous = new ArrayList<>(users);
            List<UserProfile> next = new ArrayList<>(updatedUsers);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new UserDiff(previous, next));
            users.clear();
            users.addAll(next);
            diffResult.dispatchUpdatesTo(this);
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

        private static final class UserDiff extends DiffUtil.Callback {
            private final List<UserProfile> oldItems;
            private final List<UserProfile> newItems;

            private UserDiff(@NonNull List<UserProfile> oldItems, @NonNull List<UserProfile> newItems) {
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
                UserProfile oldItem = oldItems.get(oldItemPosition);
                UserProfile newItem = newItems.get(newItemPosition);
                String oldUid = oldItem.getUid();
                String newUid = newItem.getUid();
                return oldUid != null && oldUid.equals(newUid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                UserProfile oldItem = oldItems.get(oldItemPosition);
                UserProfile newItem = newItems.get(newItemPosition);
                return equalsNullable(oldItem.getName(), newItem.getName())
                        && equalsNullable(oldItem.getEmail(), newItem.getEmail())
                        && equalsNullable(oldItem.getPhone(), newItem.getPhone());
            }

            private boolean equalsNullable(@Nullable String left, @Nullable String right) {
                return left == null ? right == null : left.equals(right);
            }
        }
    }
}
