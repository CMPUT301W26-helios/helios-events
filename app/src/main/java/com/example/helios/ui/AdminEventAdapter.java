package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.ui.event.EventUiFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying a list of events, typically for administrative purposes.
 */
public class AdminEventAdapter extends RecyclerView.Adapter<AdminEventAdapter.ViewHolder> {

    /**
     * Listener interface for handling the delete button click on an event item.
     */
    public interface OnEventDeleteListener {
        /**
         * Called when an event's delete button is clicked.
         * @param event The event associated with the clicked item.
         */
        void onEventDelete(@NonNull Event event);
    }

    /**
     * Listener interface for handling the "View Organizer" button click on an event item.
     */
    public interface OnViewOrganizerListener {
        /**
         * Called when the "View Organizer" button is clicked.
         * @param event The event whose organizer should be shown.
         */
        void onViewOrganizer(@NonNull Event event);
    }

    /**
     * Listener interface for handling the click event on an event item.
     */
    public interface OnEventClickListener {
        /**
         * Called when an event item is clicked.
         * @param event The event associated with the clicked item.
         */
        void onEventClick(@NonNull Event event);
    }

    private final List<Event> events = new ArrayList<>();
    private final OnEventDeleteListener onDelete;
    private final OnViewOrganizerListener onViewOrganizer;
    private final OnEventClickListener onEventClick;

    /**
     * Constructs an AdminEventAdapter.
     *
     * @param events          The list of events to display.
     * @param onDelete        The listener for delete button clicks.
     * @param onViewOrganizer The listener for "View Organizer" button clicks.
     * @param onEventClick    The listener for item click events.
     */
    public AdminEventAdapter(
            @NonNull List<Event> events,
            @NonNull OnEventDeleteListener onDelete,
            @NonNull OnViewOrganizerListener onViewOrganizer,
            @NonNull OnEventClickListener onEventClick
    ) {
        this.onDelete = onDelete;
        this.onViewOrganizer = onViewOrganizer;
        this.onEventClick = onEventClick;
        replaceEvents(events);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);

        holder.tvTitle.setText(EventUiFormatter.getTitle(event));
        holder.tvDescription.setText(EventUiFormatter.getDescription(event));
        holder.tvLocation.setText(EventUiFormatter.getLocationLabel(event));
        holder.tvDate.setText(EventUiFormatter.getDateLabel(event));
        holder.tvTags.setText(EventUiFormatter.getTagSummary(event, 3));
        holder.tvMaxEntrants.setText("Max: " + event.getCapacity());

        holder.btnDelete.setOnClickListener(v -> onDelete.onEventDelete(event));
        holder.btnViewOrganizer.setOnClickListener(v -> onViewOrganizer.onViewOrganizer(event));
        holder.itemView.setOnClickListener(v -> onEventClick.onEventClick(event));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public void replaceEvents(@NonNull List<Event> updatedEvents) {
        List<Event> previous = new ArrayList<>(events);
        List<Event> next = new ArrayList<>(updatedEvents);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new EventDiff(previous, next));
        events.clear();
        events.addAll(next);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * ViewHolder class for admin event items.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDescription;
        TextView tvLocation;
        TextView tvDate;
        TextView tvTags;
        TextView tvMaxEntrants;
        Button btnDelete;
        Button btnViewOrganizer;

        /**
         * Constructs a ViewHolder.
         * @param itemView The view for a single list item.
         */
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDescription = itemView.findViewById(R.id.tv_event_description);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvTags = itemView.findViewById(R.id.tv_event_tags);
            tvMaxEntrants = itemView.findViewById(R.id.tv_event_max_entrants);
            btnDelete = itemView.findViewById(R.id.btn_delete_event);
            btnViewOrganizer = itemView.findViewById(R.id.btn_view_organizer);
        }
    }

    private static final class EventDiff extends DiffUtil.Callback {
        private final List<Event> oldItems;
        private final List<Event> newItems;

        private EventDiff(@NonNull List<Event> oldItems, @NonNull List<Event> newItems) {
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
            Event oldItem = oldItems.get(oldItemPosition);
            Event newItem = newItems.get(newItemPosition);
            String oldId = oldItem.getEventId();
            String newId = newItem.getEventId();
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Event oldItem = oldItems.get(oldItemPosition);
            Event newItem = newItems.get(newItemPosition);
            return equalsNullable(oldItem.getTitle(), newItem.getTitle())
                    && equalsNullable(oldItem.getDescription(), newItem.getDescription())
                    && equalsNullable(oldItem.getLocationName(), newItem.getLocationName())
                    && oldItem.getStartTimeMillis() == newItem.getStartTimeMillis()
                    && oldItem.getCapacity() == newItem.getCapacity();
        }

        private boolean equalsNullable(@Nullable String left, @Nullable String right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
