package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.ui.event.EventUiFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for displaying a list of events.
 * It handles basic event details like title, description, location, and date.
 */
public class EventAdapter extends ListAdapter<Event, EventAdapter.EventViewHolder> {
    private static final int VIEW_TYPE_EVENT = 0;
    private static final int VIEW_TYPE_CO_ORGANIZER_INVITE = 1;
    private static final int VIEW_TYPE_PRIVATE_EVENT_INVITE = 2;

    @Nullable
    private final OnEventClickListener onEventClick;
    @Nullable
    private final OnCoOrganizerInviteActionListener onCoOrganizerInviteAction;
    @Nullable
    private final OnPrivateEventInviteActionListener onPrivateEventInviteAction;
    @Nullable
    private String organizerViewerUid;
    @Nullable
    private String entrantViewerUid;
    @NonNull
    private final Map<String, WaitingListStatus> entrantStatusesByEventId = new HashMap<>();

    /**
     * Listener interface for handling event click actions.
     */
    public interface OnEventClickListener {
        /**
         * Called when an event item is clicked.
         * @param event The event associated with the clicked item.
         */
        void onEventClick(@NonNull Event event);
    }

    public interface OnCoOrganizerInviteActionListener {
        void onAcceptInvite(@NonNull Event event);
        void onDeclineInvite(@NonNull Event event);
    }

    public interface OnPrivateEventInviteActionListener {
        void onAcceptInvite(@NonNull Event event);
        void onDeclineInvite(@NonNull Event event);
    }

    /**
     * Constructs an EventAdapter without a click listener.
     *
     * @param events The list of events to display.
     */
    public EventAdapter(@NonNull List<Event> events) {
        this(events, null, null, null);
    }

    /**
     * Constructs an EventAdapter with a click listener.
     *
     * @param events       The list of events to display.
     * @param onEventClick The listener for click events.
     */
    public EventAdapter(@NonNull List<Event> events, @Nullable OnEventClickListener onEventClick) {
        this(events, onEventClick, null, null);
    }

    public EventAdapter(
            @NonNull List<Event> events,
            @Nullable OnEventClickListener onEventClick,
            @Nullable OnCoOrganizerInviteActionListener onCoOrganizerInviteAction
    ) {
        this(events, onEventClick, onCoOrganizerInviteAction, null);
    }

    public EventAdapter(
            @NonNull List<Event> events,
            @Nullable OnEventClickListener onEventClick,
            @Nullable OnCoOrganizerInviteActionListener onCoOrganizerInviteAction,
            @Nullable OnPrivateEventInviteActionListener onPrivateEventInviteAction
    ) {
        super(new EventDiff());
        this.onEventClick = onEventClick;
        this.onCoOrganizerInviteAction = onCoOrganizerInviteAction;
        this.onPrivateEventInviteAction = onPrivateEventInviteAction;
        replaceEvents(events);
    }

    public void setOrganizerViewerUid(@Nullable String organizerViewerUid) {
        this.organizerViewerUid = organizerViewerUid;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setEntrantViewerState(
            @Nullable String entrantViewerUid,
            @Nullable Map<String, WaitingListStatus> entrantStatusesByEventId
    ) {
        this.entrantViewerUid = entrantViewerUid;
        this.entrantStatusesByEventId.clear();
        if (entrantStatusesByEventId != null) {
            this.entrantStatusesByEventId.putAll(entrantStatusesByEventId);
        }
        notifyItemRangeChanged(0, getItemCount());
    }

    public void replaceEvents(@NonNull List<Event> updatedEvents) {
        submitList(new ArrayList<>(updatedEvents));
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CO_ORGANIZER_INVITE) {
            View view = inflater.inflate(R.layout.item_coorganizer_invite, parent, false);
            return new CoOrganizerInviteViewHolder(view);
        }
        if (viewType == VIEW_TYPE_PRIVATE_EVENT_INVITE) {
            View view = inflater.inflate(R.layout.item_private_event_invite, parent, false);
            return new PrivateEventInviteViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_event, parent, false);
        return new EventCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = getItem(position);
        if (holder instanceof CoOrganizerInviteViewHolder) {
            bindCoOrganizerInvite((CoOrganizerInviteViewHolder) holder, event);
            return;
        }
        if (holder instanceof PrivateEventInviteViewHolder) {
            bindPrivateEventInvite((PrivateEventInviteViewHolder) holder, event);
            return;
        }
        bindEventCard((EventCardViewHolder) holder, event);
    }

    private void bindEventCard(@NonNull EventCardViewHolder holder, @NonNull Event event) {
        holder.tvTitle.setText(EventUiFormatter.getTitle(event));
        holder.tvLocation.setText(EventUiFormatter.getLocationLabel(event));
        holder.tvDate.setText(EventUiFormatter.getScheduleLabel(event));
        holder.tvRegistrationStatus.setText(
                EventUiFormatter.getRegistrationStatusLabel(event, System.currentTimeMillis())
        );

        if (event.getPosterImageId() != null && !event.getPosterImageId().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(event.getPosterImageId())
                    .fitCenter()
                    .placeholder(R.drawable.placeholder_event)
                    .error(R.drawable.placeholder_event)
                    .into(holder.posterImageView);
        } else {
            holder.posterImageView.setImageResource(R.drawable.placeholder_event);
        }

        String tagSummary = EventUiFormatter.getTagSummary(event, 3);
        holder.tvTags.setText(tagSummary);
        holder.tvTags.setVisibility(tagSummary.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvMaxEntrants.setText(event.getCapacity() + " seats");
        boolean showCoOrganizerBadge = organizerViewerUid != null
                && !organizerViewerUid.equals(event.getOrganizerUid())
                && event.isCoOrganizer(organizerViewerUid);
        holder.tvCoOrganizerBadge.setVisibility(showCoOrganizerBadge ? View.VISIBLE : View.GONE);
        boolean showPrivateEventBadge = event.isPrivateEvent()
                && ((organizerViewerUid != null && organizerViewerUid.equals(event.getOrganizerUid()))
                || getEntrantStatusForEvent(event) == WaitingListStatus.ACCEPTED
                || getEntrantStatusForEvent(event) == WaitingListStatus.INVITED);
        holder.tvPrivateEventBadge.setVisibility(showPrivateEventBadge ? View.VISIBLE : View.GONE);
        holder.badgeContainer.setVisibility(
                showCoOrganizerBadge || showPrivateEventBadge ? View.VISIBLE : View.GONE
        );

        holder.itemView.setOnClickListener(v -> {
            if (onEventClick != null) {
                onEventClick.onEventClick(event);
            }
        });
    }

    private void bindCoOrganizerInvite(@NonNull CoOrganizerInviteViewHolder holder, @NonNull Event event) {
        holder.tvEventTitle.setText(EventUiFormatter.getTitle(event));
        holder.btnAccept.setOnClickListener(v -> {
            if (onCoOrganizerInviteAction != null) {
                onCoOrganizerInviteAction.onAcceptInvite(event);
            }
        });
        holder.btnDecline.setOnClickListener(v -> {
            if (onCoOrganizerInviteAction != null) {
                onCoOrganizerInviteAction.onDeclineInvite(event);
            }
        });
    }

    private void bindPrivateEventInvite(
            @NonNull PrivateEventInviteViewHolder holder,
            @NonNull Event event
    ) {
        holder.tvEventTitle.setText(EventUiFormatter.getTitle(event));
        holder.tvDate.setText(EventUiFormatter.getScheduleLabel(event));
        holder.tvMaxEntrants.setText(event.getCapacity() + " seats");

        holder.itemView.setOnClickListener(v -> {
            if (onEventClick != null) {
                onEventClick.onEventClick(event);
            }
        });
        holder.btnAccept.setOnClickListener(v -> {
            if (onPrivateEventInviteAction != null) {
                onPrivateEventInviteAction.onAcceptInvite(event);
            }
        });
        holder.btnDecline.setOnClickListener(v -> {
            if (onPrivateEventInviteAction != null) {
                onPrivateEventInviteAction.onDeclineInvite(event);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        Event event = getItem(position);
        if (organizerViewerUid != null && event.isPendingCoOrganizer(organizerViewerUid)) {
            return VIEW_TYPE_CO_ORGANIZER_INVITE;
        }
        if (event.isPrivateEvent() && getEntrantStatusForEvent(event) == WaitingListStatus.INVITED) {
            return VIEW_TYPE_PRIVATE_EVENT_INVITE;
        }
        return VIEW_TYPE_EVENT;
    }

    @Nullable
    private WaitingListStatus getEntrantStatusForEvent(@NonNull Event event) {
        String eventId = event.getEventId();
        if (entrantViewerUid == null || eventId == null) {
            return null;
        }
        return entrantStatusesByEventId.get(eventId);
    }

    /**
     * ViewHolder class for event items.
     */
    static class EventViewHolder extends RecyclerView.ViewHolder {
        EventViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class EventCardViewHolder extends EventViewHolder {
        ImageView posterImageView;
        TextView tvTitle;
        TextView tvLocation;
        TextView tvDate;
        TextView tvRegistrationStatus;
        TextView tvTags;
        TextView tvMaxEntrants;
        View badgeContainer;
        TextView tvCoOrganizerBadge;
        TextView tvPrivateEventBadge;

        /**
         * Constructs an EventViewHolder.
         * @param itemView The view for a single list item.
         */
        public EventCardViewHolder(@NonNull View itemView) {
            super(itemView);
            posterImageView = itemView.findViewById(R.id.iv_event_poster);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvRegistrationStatus = itemView.findViewById(R.id.tv_event_registration_status);
            tvTags = itemView.findViewById(R.id.tv_event_tags);
            tvMaxEntrants = itemView.findViewById(R.id.tv_event_max_entrants);
            badgeContainer = itemView.findViewById(R.id.layout_event_badges_right);
            tvCoOrganizerBadge = itemView.findViewById(R.id.tv_event_coorganizer_badge);
            tvPrivateEventBadge = itemView.findViewById(R.id.tv_event_private_badge);
        }
    }

    static class CoOrganizerInviteViewHolder extends EventViewHolder {
        TextView tvEventTitle;
        View btnAccept;
        View btnDecline;

        CoOrganizerInviteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventTitle = itemView.findViewById(R.id.tv_coorganizer_invite_event_title);
            btnAccept = itemView.findViewById(R.id.btn_coorganizer_invite_accept);
            btnDecline = itemView.findViewById(R.id.btn_coorganizer_invite_decline);
        }
    }

    static class PrivateEventInviteViewHolder extends EventViewHolder {
        TextView tvEventTitle;
        TextView tvDate;
        TextView tvMaxEntrants;
        View btnAccept;
        View btnDecline;

        PrivateEventInviteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventTitle = itemView.findViewById(R.id.tv_private_invite_event_title);
            tvDate = itemView.findViewById(R.id.tv_private_invite_date);
            tvMaxEntrants = itemView.findViewById(R.id.tv_private_invite_max_entrants);
            btnAccept = itemView.findViewById(R.id.btn_private_invite_accept);
            btnDecline = itemView.findViewById(R.id.btn_private_invite_decline);
        }
    }

    private static final class EventDiff extends DiffUtil.ItemCallback<Event> {
        @Override
        public boolean areItemsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
            String oldId = oldItem.getEventId();
            String newId = newItem.getEventId();
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Event oldItem, @NonNull Event newItem) {
            return equalsNullable(oldItem.getTitle(), newItem.getTitle())
                    && equalsNullable(oldItem.getDescription(), newItem.getDescription())
                    && equalsNullable(oldItem.getLocationName(), newItem.getLocationName())
                    && equalsNullable(oldItem.getAddress(), newItem.getAddress())
                    && equalsNullable(oldItem.getPosterImageId(), newItem.getPosterImageId())
                    && equalsNullable(oldItem.getOrganizerUid(), newItem.getOrganizerUid())
                    && oldItem.getStartTimeMillis() == newItem.getStartTimeMillis()
                    && oldItem.getCapacity() == newItem.getCapacity()
                    && oldItem.isPrivateEvent() == newItem.isPrivateEvent()
                    && safeList(oldItem.getCoOrganizerUids()).equals(safeList(newItem.getCoOrganizerUids()))
                    && safeList(oldItem.getPendingCoOrganizerUids()).equals(safeList(newItem.getPendingCoOrganizerUids()));
        }

        private boolean equalsNullable(@Nullable String left, @Nullable String right) {
            return left == null ? right == null : left.equals(right);
        }

        @NonNull
        private List<String> safeList(@Nullable List<String> value) {
            return value == null ? Collections.emptyList() : value;
        }
    }
}
