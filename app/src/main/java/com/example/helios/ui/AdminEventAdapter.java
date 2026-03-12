package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminEventAdapter extends RecyclerView.Adapter<AdminEventAdapter.ViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(@NonNull Event event);
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final List<Event> events;
    private final OnEventClickListener onClick;

    public AdminEventAdapter(@NonNull List<Event> events, @NonNull OnEventClickListener onClick) {
        this.events = events;
        this.onClick = onClick;
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

        // Delete button only — does not make the whole row clickable
        holder.btnDelete.setOnClickListener(v -> onClick.onEventClick(event));
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDescription;
        TextView tvLocation;
        TextView tvDate;
        TextView tvTags;
        TextView tvMaxEntrants;
        Button btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDescription = itemView.findViewById(R.id.tv_event_description);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvTags = itemView.findViewById(R.id.tv_event_tags);
            tvMaxEntrants = itemView.findViewById(R.id.tv_event_max_entrants);
            btnDelete = itemView.findViewById(R.id.btn_delete_event);
        }
    }
}