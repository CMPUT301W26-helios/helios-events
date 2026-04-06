package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.ui.event.EventUiFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying event poster images in the admin panel.
 * Each item shows the poster thumbnail, the event title, its start date, and a
 * Delete button that removes the poster URI from the event (without deleting the
 * event itself).
 */
public class AdminImageAdapter extends RecyclerView.Adapter<AdminImageAdapter.ViewHolder> {

    public interface OnImageDeleteListener {
        void onImageDelete(@NonNull Event event);
    }

    private final List<Event> events = new ArrayList<>();
    private final OnImageDeleteListener onDelete;

    public AdminImageAdapter(
            @NonNull List<Event> events,
            @NonNull OnImageDeleteListener onDelete
    ) {
        this.onDelete = onDelete;
        replaceEvents(events);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);

        // Poster preview
        String posterUri = event.getPosterImageId();
        if (posterUri != null && !posterUri.trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(posterUri)
                    .placeholder(R.drawable.placeholder_event)
                    .error(R.drawable.placeholder_event)
                    .into(holder.ivPreview);
        } else {
            holder.ivPreview.setImageResource(R.drawable.placeholder_event);
        }

        // Event title
        holder.tvLabel.setText(EventUiFormatter.getTitle(event));


        holder.btnDelete.setOnClickListener(v -> onDelete.onImageDelete(event));
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivPreview;
        final TextView tvLabel;
        final Button btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPreview = itemView.findViewById(R.id.iv_image_preview);
            tvLabel   = itemView.findViewById(R.id.tv_image_label);
            btnDelete = itemView.findViewById(R.id.btn_delete_image);
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
            return equalsNullable(oldItem.getPosterImageId(), newItem.getPosterImageId())
                    && equalsNullable(oldItem.getTitle(), newItem.getTitle());
        }

        private boolean equalsNullable(@Nullable String left, @Nullable String right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
