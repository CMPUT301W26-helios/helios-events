package com.example.helios.model;

import androidx.annotation.Nullable;

public class EventComment {
    private String commentId;
    private String eventId;
    private String authorUid;
    private String authorNameSnapshot;
    private String body;
    private String parentCommentId; // null for top-level comments
    private long createdAtMillis;
    private long updatedAtMillis;
    private int likeCount;
    private boolean isDeleted;

    public EventComment() {
        // Required for Firestore
    }

    public EventComment(String commentId,
                        String eventId,
                        String authorUid,
                        String authorNameSnapshot,
                        String body,
                        @Nullable String parentCommentId,
                        long createdAtMillis,
                        long updatedAtMillis,
                        int likeCount,
                        boolean isDeleted) {
        this.commentId = commentId;
        this.eventId = eventId;
        this.authorUid = authorUid;
        this.authorNameSnapshot = authorNameSnapshot;
        this.body = body;
        this.parentCommentId = parentCommentId;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.likeCount = likeCount;
        this.isDeleted = isDeleted;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAuthorUid() {
        return authorUid;
    }

    public void setAuthorUid(String authorUid) {
        this.authorUid = authorUid;
    }

    public String getAuthorNameSnapshot() {
        return authorNameSnapshot;
    }

    public void setAuthorNameSnapshot(String authorNameSnapshot) {
        this.authorNameSnapshot = authorNameSnapshot;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Nullable
    public String getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(@Nullable String parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = Math.max(0, likeCount);
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public boolean isTopLevel() {
        return parentCommentId == null || parentCommentId.trim().isEmpty();
    }
}
