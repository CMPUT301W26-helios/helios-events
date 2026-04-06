package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.NotificationAudience;
import com.example.helios.model.NotificationRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * RecyclerView adapter for the admin notification audit log.
 */
public class AdminNotificationAdapter extends RecyclerView.Adapter<AdminNotificationAdapter.ViewHolder> {

    public interface OnNotificationLongPressListener {
        void onNotificationLongPress(@NonNull NotificationRecord record);
    }

    private static final int MESSAGE_PREVIEW_LIMIT = 80;

    private final List<NotificationRecord> notifications = new ArrayList<>();
    private final Map<String, String> eventTitlesById = new HashMap<>();
    private final OnNotificationLongPressListener onNotificationLongPress;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public AdminNotificationAdapter(
            @NonNull List<NotificationRecord> notifications,
            @NonNull Map<String, String> eventTitlesById,
            @NonNull OnNotificationLongPressListener onNotificationLongPress
    ) {
        this.onNotificationLongPress = onNotificationLongPress;
        replaceEventTitles(eventTitlesById);
        replaceNotifications(notifications);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationRecord record = notifications.get(position);

        String eventId = record.getEventId();
        if (isNonEmpty(eventId)) {
            String eventTitle = eventTitlesById.get(eventId);
            if (!isNonEmpty(eventTitle)) {
                eventTitle = "Event ID: " + eventId;
            }
            holder.tvEventTitle.setText(eventTitle);
            holder.tvEventTitle.setVisibility(View.VISIBLE);
        } else {
            holder.tvEventTitle.setVisibility(View.GONE);
        }

        holder.tvAudience.setText(formatAudience(record.getAudience()));
        holder.tvTitle.setText(nonEmptyOr(record.getTitle(), "Untitled notification"));
        holder.tvMessage.setText(truncate(record.getMessage()));
        holder.tvTime.setText(formatTime(record.getSentAtMillis()));

        holder.itemView.setOnLongClickListener(v -> {
            onNotificationLongPress.onNotificationLongPress(record);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void replaceNotifications(@NonNull List<NotificationRecord> updatedNotifications) {
        List<NotificationRecord> previous = new ArrayList<>(notifications);
        List<NotificationRecord> next = new ArrayList<>(updatedNotifications);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new NotificationDiff(previous, next));
        notifications.clear();
        notifications.addAll(next);
        diffResult.dispatchUpdatesTo(this);
    }

    public void replaceEventTitles(@NonNull Map<String, String> updatedEventTitlesById) {
        eventTitlesById.clear();
        eventTitlesById.putAll(updatedEventTitlesById);
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    private String formatTime(long millis) {
        if (millis <= 0) {
            return "Unknown time";
        }
        return dateFormat.format(new Date(millis));
    }

    @NonNull
    private String truncate(String message) {
        String safeMessage = nonEmptyOr(message, "(no message)");
        if (safeMessage.length() <= MESSAGE_PREVIEW_LIMIT) {
            return safeMessage;
        }
        return safeMessage.substring(0, MESSAGE_PREVIEW_LIMIT - 3) + "...";
    }

    @NonNull
    private String formatAudience(NotificationAudience audience) {
        if (audience == null) {
            return "Audience: unknown";
        }

        String value = audience.name().toLowerCase(Locale.US).replace('_', ' ');
        String[] parts = value.split(" ");
        StringBuilder builder = new StringBuilder("Audience: ");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (builder.charAt(builder.length() - 1) != ' ') {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    @NonNull
    private String nonEmptyOr(String value, String fallback) {
        if (!isNonEmpty(value)) {
            return fallback;
        }
        return value.trim();
    }

    private boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class NotificationDiff extends DiffUtil.Callback {
        private final List<NotificationRecord> oldItems;
        private final List<NotificationRecord> newItems;

        private NotificationDiff(
                @NonNull List<NotificationRecord> oldItems,
                @NonNull List<NotificationRecord> newItems
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
            NotificationRecord oldItem = oldItems.get(oldItemPosition);
            NotificationRecord newItem = newItems.get(newItemPosition);
            String oldId = oldItem.getNotificationId();
            String newId = newItem.getNotificationId();
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            NotificationRecord oldItem = oldItems.get(oldItemPosition);
            NotificationRecord newItem = newItems.get(newItemPosition);
            return equalsNullable(oldItem.getEventId(), newItem.getEventId())
                    && equalsNullable(oldItem.getTitle(), newItem.getTitle())
                    && equalsNullable(oldItem.getMessage(), newItem.getMessage())
                    && oldItem.getSentAtMillis() == newItem.getSentAtMillis()
                    && oldItem.getAudience() == newItem.getAudience();
        }

        private boolean equalsNullable(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvEventTitle;
        private final TextView tvAudience;
        private final TextView tvTitle;
        private final TextView tvMessage;
        private final TextView tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventTitle = itemView.findViewById(R.id.tv_admin_notification_event);
            tvAudience = itemView.findViewById(R.id.tv_admin_notification_audience);
            tvTitle = itemView.findViewById(R.id.tv_admin_notification_title);
            tvMessage = itemView.findViewById(R.id.tv_admin_notification_message);
            tvTime = itemView.findViewById(R.id.tv_admin_notification_time);
        }
    }
}
