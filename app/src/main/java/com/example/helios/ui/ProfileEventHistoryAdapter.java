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

import com.example.helios.R;
import com.example.helios.model.WaitingListStatus;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the profile event history lists.
 */
public class ProfileEventHistoryAdapter
        extends ListAdapter<ProfileEventHistoryAdapter.HistoryItem, ProfileEventHistoryAdapter.ViewHolder> {

    @Nullable
    private final OnHistoryClickListener onHistoryClickListener;

    public interface OnHistoryClickListener {
        void onHistoryClick(@NonNull HistoryItem item);
    }

    public static final class HistoryItem {
        @NonNull private final String stableId;
        @Nullable private final String eventId;
        @NonNull private final String title;
        @NonNull private final String subtext;
        @NonNull private final WaitingListStatus status;
        private final boolean clickable;

        public HistoryItem(
                @NonNull String stableId,
                @Nullable String eventId,
                @NonNull String title,
                @NonNull String subtext,
                @NonNull WaitingListStatus status,
                boolean clickable
        ) {
            this.stableId = stableId;
            this.eventId = eventId;
            this.title = title;
            this.subtext = subtext;
            this.status = status;
            this.clickable = clickable;
        }

        @NonNull
        public String getStableId() {
            return stableId;
        }

        @Nullable
        public String getEventId() {
            return eventId;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getSubtext() {
            return subtext;
        }

        @NonNull
        public WaitingListStatus getStatus() {
            return status;
        }

        public boolean isClickable() {
            return clickable;
        }
    }

    public ProfileEventHistoryAdapter(@Nullable OnHistoryClickListener onHistoryClickListener) {
        super(new HistoryItemDiff());
        this.onHistoryClickListener = onHistoryClickListener;
    }

    public void replaceItems(@NonNull List<HistoryItem> items) {
        submitList(new ArrayList<>(items));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = getItem(position);
        holder.tvEventName.setText(item.getTitle());
        holder.tvEventSubtext.setText(item.getSubtext());
        holder.tvEventSubtext.setVisibility(item.getSubtext().isEmpty() ? View.GONE : View.VISIBLE);

        holder.ivEventStatus.setImageResource(getStatusIconRes(item.getStatus()));
        holder.ivEventStatus.setImageTintList(null);
        holder.ivEventStatus.setColorFilter(MaterialColors.getColor(
                holder.itemView,
                getStatusTintAttr(item.getStatus())
        ));

        holder.ivChevron.setVisibility(item.isClickable() ? View.VISIBLE : View.GONE);
        holder.itemView.setEnabled(item.isClickable());
        holder.itemView.setAlpha(item.isClickable() ? 1f : 0.7f);
        holder.itemView.setOnClickListener(item.isClickable() && onHistoryClickListener != null
                ? v -> onHistoryClickListener.onHistoryClick(item)
                : null);
    }

    private int getStatusIconRes(@NonNull WaitingListStatus status) {
        switch (status) {
            case WAITING:
                return android.R.drawable.ic_popup_sync;
            case INVITED:
                return android.R.drawable.ic_menu_send;
            case ACCEPTED:
                return android.R.drawable.checkbox_on_background;
            case DECLINED:
                return android.R.drawable.ic_delete;
            case CANCELLED:
                return android.R.drawable.ic_menu_close_clear_cancel;
            case NOT_SELECTED:
            default:
                return android.R.drawable.ic_menu_recent_history;
        }
    }

    private int getStatusTintAttr(@NonNull WaitingListStatus status) {
        switch (status) {
            case WAITING:
                return androidx.appcompat.R.attr.colorPrimary;
            case INVITED:
                return com.google.android.material.R.attr.colorTertiary;
            case ACCEPTED:
                return androidx.appcompat.R.attr.colorPrimary;
            case DECLINED:
            default:
                return com.google.android.material.R.attr.colorOnSurfaceVariant;
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivEventStatus;
        private final TextView tvEventName;
        private final TextView tvEventSubtext;
        private final ImageView ivChevron;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivEventStatus = itemView.findViewById(R.id.iv_event_status);
            tvEventName = itemView.findViewById(R.id.tv_event_name);
            tvEventSubtext = itemView.findViewById(R.id.tv_event_subtext);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
        }
    }

    private static final class HistoryItemDiff extends DiffUtil.ItemCallback<HistoryItem> {
        @Override
        public boolean areItemsTheSame(@NonNull HistoryItem oldItem, @NonNull HistoryItem newItem) {
            return oldItem.getStableId().equals(newItem.getStableId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull HistoryItem oldItem, @NonNull HistoryItem newItem) {
            return oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getSubtext().equals(newItem.getSubtext())
                    && oldItem.getStatus() == newItem.getStatus()
                    && oldItem.isClickable() == newItem.isClickable();
        }
    }
}
