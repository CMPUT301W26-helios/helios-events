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

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final List<Event> events;

    @Nullable
    private final OnEventClickListener onEventClick;

    public interface OnEventClickListener {
        void onEventClick(@NonNull Event event);
    }

    public EventAdapter(@NonNull List<Event> events) {
        this(events, null);
    }

    public EventAdapter(@NonNull List<Event> events, @Nullable OnEventClickListener onEventClick) {
        this.events = events;
        this.onEventClick = onEventClick;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);

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

        String guidelines = nonEmptyOr(event.getLotteryGuidelines(), null);
        holder.tvTags.setText(guidelines != null ? guidelines : "No lottery details");

        holder.tvMaxEntrants.setText("Max: " + event.getCapacity());

        holder.itemView.setOnClickListener(v -> {
            if (onEventClick != null) {
                onEventClick.onEventClick(event);
            }
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private String nonEmptyOr(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDescription;
        TextView tvLocation;
        TextView tvDate;
        TextView tvTags;
        TextView tvMaxEntrants;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDescription = itemView.findViewById(R.id.tv_event_description);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvTags = itemView.findViewById(R.id.tv_event_tags);
            tvMaxEntrants = itemView.findViewById(R.id.tv_event_max_entrants);
        }
    }
}