package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.NotificationRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends ListAdapter<NotificationRecord, NotificationAdapter.ViewHolder> {
    public interface OnNotificationClickListener {
        void onNotificationClick(@NonNull NotificationRecord notification);
    }

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault());
    private final OnNotificationClickListener onNotificationClick;

    public NotificationAdapter(List<NotificationRecord> notifications) {
        this(notifications, null);
    }

    public NotificationAdapter(
            List<NotificationRecord> notifications,
            OnNotificationClickListener onNotificationClick
    ) {
        super(new NotificationDiff());
        this.onNotificationClick = onNotificationClick;
        replaceNotifications(notifications);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationRecord n = getItem(position);
        holder.tvTitle.setText(n.getTitle());
        holder.tvMessage.setText(n.getMessage());
        holder.tvTime.setText(dateFormat.format(new Date(n.getSentAtMillis())));
        holder.itemView.setOnClickListener(v -> {
            if (onNotificationClick != null) {
                onNotificationClick.onNotificationClick(n);
            }
        });
    }


    public void replaceNotifications(@NonNull List<NotificationRecord> updatedNotifications) {
        submitList(new ArrayList<>(updatedNotifications));
    }

    private static final class NotificationDiff extends DiffUtil.ItemCallback<NotificationRecord> {
        @Override
        public boolean areItemsTheSame(@NonNull NotificationRecord oldItem, @NonNull NotificationRecord newItem) {
            String oldId = oldItem.getNotificationId();
            String newId = newItem.getNotificationId();
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull NotificationRecord oldItem, @NonNull NotificationRecord newItem) {
            return equalsNullable(oldItem.getTitle(), newItem.getTitle())
                    && equalsNullable(oldItem.getMessage(), newItem.getMessage())
                    && oldItem.getSentAtMillis() == newItem.getSentAtMillis();
        }

        private boolean equalsNullable(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notif_title);
            tvMessage = itemView.findViewById(R.id.tv_notif_message);
            tvTime = itemView.findViewById(R.id.tv_notif_time);
        }
    }
}
