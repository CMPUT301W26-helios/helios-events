package com.example.helios.ui.event;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.EventComment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventCommentsAdapter extends RecyclerView.Adapter<EventCommentsAdapter.CommentViewHolder> {

    public interface CommentActionListener {
        void onReply(@NonNull EventComment comment);
        void onLikeToggle(@NonNull EventComment comment, boolean currentlyLiked);
        void onDelete(@NonNull EventComment comment);
    }

    private final List<EventComment> topLevelComments = new ArrayList<>();
    private final Map<String, List<EventComment>> repliesByParent = new HashMap<>();
    private final Set<String> likedCommentIds = new HashSet<>();
    private final CommentActionListener actionListener;
    private final RecyclerView.RecycledViewPool sharedReplyPool = new RecyclerView.RecycledViewPool();

    @Nullable
    private String currentUserUid;
    private boolean currentUserAdmin = false;
    @Nullable
    private String organizerUid;

    public EventCommentsAdapter(@NonNull CommentActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setCurrentUser(@Nullable String uid, boolean isAdmin) {
        this.currentUserUid = uid;
        this.currentUserAdmin = isAdmin;
        notifyDataSetChanged();
    }

    public void setOrganizerUid(@Nullable String organizerUid) {
        this.organizerUid = organizerUid;
        notifyDataSetChanged();
    }

    public void setTopLevelComments(@NonNull List<EventComment> comments) {
        topLevelComments.clear();
        topLevelComments.addAll(comments);
        topLevelComments.sort((a, b) -> {
            boolean ap = a != null && a.isPinned();
            boolean bp = b != null && b.isPinned();
            if (ap != bp) return ap ? -1 : 1;
            long at = a != null ? a.getCreatedAtMillis() : 0L;
            long bt = b != null ? b.getCreatedAtMillis() : 0L;
            return Long.compare(bt, at);
        });
        notifyDataSetChanged();
    }

    public void setReplies(@NonNull String parentCommentId, @NonNull List<EventComment> replies) {
        repliesByParent.put(parentCommentId, new ArrayList<>(replies));
        notifyDataSetChanged();
    }

    public void setLikedCommentIds(@NonNull Set<String> likedIds) {
        likedCommentIds.clear();
        likedCommentIds.addAll(likedIds);
        notifyDataSetChanged();
    }

    @NonNull
    public List<String> getAllVisibleCommentIds() {
        List<String> ids = new ArrayList<>();
        for (EventComment top : topLevelComments) {
            if (isNonEmpty(top.getCommentId())) {
                ids.add(top.getCommentId());
            }
            List<EventComment> replies = repliesByParent.get(top.getCommentId());
            if (replies == null) continue;
            for (EventComment reply : replies) {
                if (isNonEmpty(reply.getCommentId())) {
                    ids.add(reply.getCommentId());
                }
            }
        }
        return ids;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_comment, parent, false);
        return new CommentViewHolder(view, sharedReplyPool);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        EventComment comment = topLevelComments.get(position);
        holder.bind(comment);

        String commentId = comment.getCommentId();
        List<EventComment> replies = isNonEmpty(commentId)
                ? repliesByParent.getOrDefault(commentId, new ArrayList<>())
                : new ArrayList<>();
        holder.bindReplies(replies);
    }

    @Override
    public int getItemCount() {
        return topLevelComments.size();
    }

    private boolean canDelete(@Nullable EventComment comment) {
        if (comment == null) return false;
        if (currentUserAdmin) return true;
        if (!isNonEmpty(currentUserUid)) return false;
        if (currentUserUid.equals(comment.getAuthorUid())) return true;
        return organizerUid != null && organizerUid.equals(currentUserUid);
    }

    private boolean isOrganizerAuthor(@Nullable EventComment comment) {
        return comment != null
                && organizerUid != null
                && isNonEmpty(comment.getAuthorUid())
                && organizerUid.equals(comment.getAuthorUid());
    }

    private boolean isLiked(@Nullable EventComment comment) {
        return comment != null && isNonEmpty(comment.getCommentId()) && likedCommentIds.contains(comment.getCommentId());
    }

    private CharSequence relativeTime(long millis) {
        if (millis <= 0L) return "";
        return DateUtils.getRelativeTimeSpanString(
                millis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        );
    }

    private String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private boolean isNonEmpty(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAuthor;
        private final TextView tvRole;
        private final TextView tvTime;
        private final TextView tvBody;
        private final TextView tvDelete;
        private final ImageView ivLike;
        private final TextView tvLikeCount;
        private final LinearLayout actionLike;
        private final LinearLayout actionReply;
        private final RecyclerView rvReplies;
        private final ReplyAdapter replyAdapter;

        CommentViewHolder(@NonNull View itemView, @NonNull RecyclerView.RecycledViewPool recycledViewPool) {
            super(itemView);
            tvAuthor = itemView.findViewById(R.id.text_comment_author);
            tvRole = itemView.findViewById(R.id.text_comment_role);
            tvTime = itemView.findViewById(R.id.text_comment_time);
            tvBody = itemView.findViewById(R.id.text_comment_body);
            tvDelete = itemView.findViewById(R.id.button_comment_delete);
            ivLike = itemView.findViewById(R.id.image_comment_like);
            tvLikeCount = itemView.findViewById(R.id.text_comment_like_count);
            actionLike = itemView.findViewById(R.id.action_comment_like);
            actionReply = itemView.findViewById(R.id.action_comment_reply);
            rvReplies = itemView.findViewById(R.id.rv_comment_replies);

            rvReplies.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            rvReplies.setNestedScrollingEnabled(false);
            rvReplies.setRecycledViewPool(recycledViewPool);
            replyAdapter = new ReplyAdapter();
            rvReplies.setAdapter(replyAdapter);
        }

        void bind(@NonNull EventComment comment) {
            tvAuthor.setText(nonEmptyOr(comment.getAuthorNameSnapshot(), "Anonymous"));
            if (tvRole != null && isOrganizerAuthor(comment) && comment.isTopLevel()) {
                tvRole.setVisibility(View.VISIBLE);
                tvRole.setText("Organizer");
            } else if (tvRole != null) {
                tvRole.setVisibility(View.GONE);
                tvRole.setText("");
            }
            tvTime.setText(relativeTime(comment.getCreatedAtMillis()));
            tvBody.setText(nonEmptyOr(comment.getBody(), ""));
            tvLikeCount.setText(String.valueOf(Math.max(0, comment.getLikeCount())));

            boolean liked = isLiked(comment);
            ivLike.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            actionLike.setOnClickListener(v -> actionListener.onLikeToggle(comment, liked));
            actionReply.setOnClickListener(v -> actionListener.onReply(comment));

            if (canDelete(comment)) {
                tvDelete.setVisibility(View.VISIBLE);
                tvDelete.setOnClickListener(v -> actionListener.onDelete(comment));
            } else {
                tvDelete.setVisibility(View.GONE);
                tvDelete.setOnClickListener(null);
            }
        }

        void bindReplies(@NonNull List<EventComment> replies) {
            replyAdapter.setItems(replies);
            rvReplies.setVisibility(replies.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    class ReplyAdapter extends RecyclerView.Adapter<ReplyAdapter.ReplyViewHolder> {
        private final List<EventComment> replies = new ArrayList<>();

        void setItems(@NonNull List<EventComment> items) {
            replies.clear();
            replies.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ReplyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_event_comment_reply, parent, false);
            return new ReplyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ReplyViewHolder holder, int position) {
            EventComment reply = replies.get(position);
            holder.bind(reply);
        }

        @Override
        public int getItemCount() {
            return replies.size();
        }

        class ReplyViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvAuthor;
            private final TextView tvRole;
            private final TextView tvTime;
            private final TextView tvBody;
            private final TextView tvDelete;
            private final ImageView ivLike;
            private final TextView tvLikeCount;
            private final LinearLayout actionLike;

            ReplyViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAuthor = itemView.findViewById(R.id.text_comment_reply_author);
                tvRole = itemView.findViewById(R.id.text_comment_reply_role);
                tvTime = itemView.findViewById(R.id.text_comment_reply_time);
                tvBody = itemView.findViewById(R.id.text_comment_reply_body);
                tvDelete = itemView.findViewById(R.id.button_comment_reply_delete);
                ivLike = itemView.findViewById(R.id.image_comment_reply_like);
                tvLikeCount = itemView.findViewById(R.id.text_comment_reply_like_count);
                actionLike = itemView.findViewById(R.id.action_comment_reply_like);
            }

            void bind(@NonNull EventComment reply) {
                tvAuthor.setText(nonEmptyOr(reply.getAuthorNameSnapshot(), "Anonymous"));
                if (tvRole != null && isOrganizerAuthor(reply)) {
                    tvRole.setVisibility(View.VISIBLE);
                    tvRole.setText("Organizer");
                } else if (tvRole != null) {
                    tvRole.setVisibility(View.GONE);
                    tvRole.setText("");
                }
                tvTime.setText(relativeTime(reply.getCreatedAtMillis()));
                tvBody.setText(nonEmptyOr(reply.getBody(), ""));
                tvLikeCount.setText(String.valueOf(Math.max(0, reply.getLikeCount())));

                boolean liked = isLiked(reply);
                ivLike.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                actionLike.setOnClickListener(v -> actionListener.onLikeToggle(reply, liked));

                if (canDelete(reply)) {
                    tvDelete.setVisibility(View.VISIBLE);
                    tvDelete.setOnClickListener(v -> actionListener.onDelete(reply));
                } else {
                    tvDelete.setVisibility(View.GONE);
                    tvDelete.setOnClickListener(null);
                }
            }
        }
    }
}
