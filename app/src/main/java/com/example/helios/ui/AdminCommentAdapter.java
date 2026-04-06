package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.EventComment;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for admin-side comment moderation.
 */
public class AdminCommentAdapter extends RecyclerView.Adapter<AdminCommentAdapter.ViewHolder> {
    public interface OnDeleteCommentListener {
        void onDeleteComment(@NonNull EventComment comment);
    }

    private final List<EventComment> comments = new ArrayList<>();
    private final Map<String, String> eventTitlesById = new HashMap<>();
    private final OnDeleteCommentListener onDeleteComment;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

    public AdminCommentAdapter(
            @NonNull List<EventComment> comments,
            @NonNull Map<String, String> eventTitlesById,
            @NonNull OnDeleteCommentListener onDeleteComment
    ) {
        this.onDeleteComment = onDeleteComment;
        replaceEventTitles(eventTitlesById);
        replaceComments(comments);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_comment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventComment comment = comments.get(position);
        holder.tvEvent.setText(resolveEventTitle(comment));
        holder.tvAuthor.setText(nonEmptyOr(comment.getAuthorNameSnapshot(), "Anonymous"));
        holder.tvBody.setText(nonEmptyOr(comment.getBody(), "(empty comment)"));
        holder.tvMeta.setText(buildMeta(comment));
        holder.btnDelete.setOnClickListener(v -> onDeleteComment.onDeleteComment(comment));
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void replaceComments(@NonNull List<EventComment> updatedComments) {
        List<EventComment> previous = new ArrayList<>(comments);
        List<EventComment> next = new ArrayList<>(updatedComments);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new CommentDiff(previous, next));
        comments.clear();
        comments.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void replaceEventTitles(@NonNull Map<String, String> updatedEventTitles) {
        eventTitlesById.clear();
        eventTitlesById.putAll(updatedEventTitles);
        notifyDataSetChanged();
    }

    @NonNull
    private String resolveEventTitle(@NonNull EventComment comment) {
        String eventId = comment.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            return "Unknown event";
        }
        String title = eventTitlesById.get(eventId);
        return nonEmptyOr(title, "Event " + eventId);
    }

    @NonNull
    private String buildMeta(@NonNull EventComment comment) {
        StringBuilder builder = new StringBuilder();
        if (comment.isPinned()) {
            builder.append("Pinned");
        } else if (!comment.isTopLevel()) {
            builder.append("Reply");
        } else {
            builder.append("Top-level");
        }
        builder.append(" | ");
        builder.append(dateFormat.format(new Date(comment.getCreatedAtMillis())));
        return builder.toString();
    }

    @NonNull
    private String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEvent;
        final TextView tvAuthor;
        final TextView tvBody;
        final TextView tvMeta;
        final MaterialButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEvent = itemView.findViewById(R.id.tv_admin_comment_event);
            tvAuthor = itemView.findViewById(R.id.tv_admin_comment_author);
            tvBody = itemView.findViewById(R.id.tv_admin_comment_body);
            tvMeta = itemView.findViewById(R.id.tv_admin_comment_meta);
            btnDelete = itemView.findViewById(R.id.btn_admin_comment_delete);
        }
    }

    private static final class CommentDiff extends DiffUtil.Callback {
        private final List<EventComment> oldItems;
        private final List<EventComment> newItems;

        private CommentDiff(@NonNull List<EventComment> oldItems, @NonNull List<EventComment> newItems) {
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
            String oldId = oldItems.get(oldItemPosition).getCommentId();
            String newId = newItems.get(newItemPosition).getCommentId();
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            EventComment oldItem = oldItems.get(oldItemPosition);
            EventComment newItem = newItems.get(newItemPosition);
            return equalsNullable(oldItem.getBody(), newItem.getBody())
                    && equalsNullable(oldItem.getAuthorNameSnapshot(), newItem.getAuthorNameSnapshot())
                    && oldItem.getCreatedAtMillis() == newItem.getCreatedAtMillis()
                    && oldItem.isPinned() == newItem.isPinned()
                    && oldItem.isDeleted() == newItem.isDeleted();
        }

        private boolean equalsNullable(@Nullable String left, @Nullable String right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
