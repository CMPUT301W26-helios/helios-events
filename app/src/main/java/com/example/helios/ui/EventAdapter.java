package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying a list of events.
 * It handles basic event details like title, description, location, and date.
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private static final int VIEW_TYPE_EVENT = 0;
    private static final int VIEW_TYPE_CO_ORGANIZER_INVITE = 1;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final List<Event> events;

    @Nullable
    private final OnEventClickListener onEventClick;
    @Nullable
    private final OnCoOrganizerInviteActionListener onCoOrganizerInviteAction;
    @Nullable
    private String organizerViewerUid;

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

    /**
     * Constructs an EventAdapter without a click listener.
     *
     * @param events The list of events to display.
     */
    public EventAdapter(@NonNull List<Event> events) {
        this(events, null, null);
    }

    /**
     * Constructs an EventAdapter with a click listener.
     *
     * @param events       The list of events to display.
     * @param onEventClick The listener for click events.
     */
    public EventAdapter(@NonNull List<Event> events, @Nullable OnEventClickListener onEventClick) {
        this(events, onEventClick, null);
    }

    public EventAdapter(
            @NonNull List<Event> events,
            @Nullable OnEventClickListener onEventClick,
            @Nullable OnCoOrganizerInviteActionListener onCoOrganizerInviteAction
    ) {
        this.events = events;
        this.onEventClick = onEventClick;
        this.onCoOrganizerInviteAction = onCoOrganizerInviteAction;
    }

    public void setOrganizerViewerUid(@Nullable String organizerViewerUid) {
        this.organizerViewerUid = organizerViewerUid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CO_ORGANIZER_INVITE) {
            View view = inflater.inflate(R.layout.item_coorganizer_invite, parent, false);
            return new CoOrganizerInviteViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_event, parent, false);
        return new EventCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        if (holder instanceof CoOrganizerInviteViewHolder) {
            bindCoOrganizerInvite((CoOrganizerInviteViewHolder) holder, event);
            return;
        }
        bindEventCard((EventCardViewHolder) holder, event);
    }

    private void bindEventCard(@NonNull EventCardViewHolder holder, @NonNull Event event) {
        holder.tvTitle.setText(nonEmptyOr(event.getTitle(), "Untitled Event"));
        holder.tvDescription.setText(nonEmptyOr(event.getDescription(), ""));

        String location = nonEmptyOr(event.getLocationName(), null);
        if (location == null) {
            location = nonEmptyOr(event.getAddress(), "No location");
        }
        holder.tvLocation.setText(location);

        long startMillis = event.getStartTimeMillis();
        if (startMillis > 0) {
            holder.tvDate.setText(dateFormat.format(new Date(startMillis)));
        } else {
            holder.tvDate.setText("TBA");
        }

        java.util.List<String> interests = event.getInterests();
        if (interests != null && !interests.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < interests.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(interests.get(i));
            }
            holder.tvTags.setText("Tags: " + sb);
        } else {
            String guidelines = nonEmptyOr(event.getLotteryGuidelines(), null);
            holder.tvTags.setText(guidelines != null ? guidelines : "No lottery details");
        }

        holder.tvMaxEntrants.setText("Max: " + event.getCapacity());
        boolean showCoOrganizerBadge = organizerViewerUid != null
                && !organizerViewerUid.equals(event.getOrganizerUid())
                && event.isCoOrganizer(organizerViewerUid);
        holder.tvCoOrganizerBadge.setVisibility(showCoOrganizerBadge ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (onEventClick != null) {
                onEventClick.onEventClick(event);
            }
        });
    }

    private void bindCoOrganizerInvite(@NonNull CoOrganizerInviteViewHolder holder, @NonNull Event event) {
        holder.tvEventTitle.setText(nonEmptyOr(event.getTitle(), "Untitled Event"));
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

    @Override
    public int getItemCount() {
        return events.size();
    }

    @Override
    public int getItemViewType(int position) {
        Event event = events.get(position);
        if (organizerViewerUid != null && event.isPendingCoOrganizer(organizerViewerUid)) {
            return VIEW_TYPE_CO_ORGANIZER_INVITE;
        }
        return VIEW_TYPE_EVENT;
    }

    private String nonEmptyOr(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
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
        TextView tvTitle;
        TextView tvDescription;
        TextView tvLocation;
        TextView tvDate;
        TextView tvTags;
        TextView tvMaxEntrants;
        TextView tvCoOrganizerBadge;

        /**
         * Constructs an EventViewHolder.
         * @param itemView The view for a single list item.
         */
        public EventCardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDescription = itemView.findViewById(R.id.tv_event_description);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvTags = itemView.findViewById(R.id.tv_event_tags);
            tvMaxEntrants = itemView.findViewById(R.id.tv_event_max_entrants);
            tvCoOrganizerBadge = itemView.findViewById(R.id.tv_event_coorganizer_badge);
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
}
