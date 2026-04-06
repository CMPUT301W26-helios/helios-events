package com.example.helios.model;

import androidx.annotation.Nullable;

/**
 * Represents a comment made on a specific event.
 * Stores details about the author, the message content, and interaction metrics (likes/pins).
 * 
 * <p>Role in the application: allows Entrants and Organizers to discuss and ask questions about an event.
 */
public class EventComment {
    private String commentId;
    private String eventId;
    private String authorUid;
    private String authorNameSnapshot;
    private String authorProfileImageUrlSnapshot;
    private String body;
    private String parentCommentId; // null for top-level comments
    private long createdAtMillis;
    private long updatedAtMillis;
    private int likeCount;
    private boolean isDeleted;
    private boolean pinned;

    /**
     * Default constructor required for Firestore deserialization.
     */
    public EventComment() {
        // Required for Firestore
    }

    /**
     * Constructs a new EventComment with specified attributes.
     *
     * @param commentId          The unique identifier for the comment.
     * @param eventId            The unique identifier for the associated event.
     * @param authorUid          The unique identifier of the user authoring the comment.
     * @param authorNameSnapshot A snapshot of the author's name at the time of creation.
     * @param authorProfileImageUrlSnapshot A snapshot of the author's profile image URL at creation time.
     * @param body               The text body of the comment.
     * @param parentCommentId    The ID of the parent comment, or null if it's a top-level comment.
     * @param createdAtMillis    Creation timestamp in epoch millis.
     * @param updatedAtMillis    Last updated timestamp in epoch millis.
     * @param likeCount          Number of likes this comment has received.
     * @param isDeleted          Whether this comment has been marked as deleted (e.g. for moderation).
     * @param pinned             Whether this comment is pinned to the top of the event's thread.
     */
    public EventComment(String commentId,
                        String eventId,
                        String authorUid,
                        String authorNameSnapshot,
                        @Nullable String authorProfileImageUrlSnapshot,
                        String body,
                        @Nullable String parentCommentId,
                        long createdAtMillis,
                        long updatedAtMillis,
                        int likeCount,
                        boolean isDeleted,
                        boolean pinned) {
        this.commentId = commentId;
        this.eventId = eventId;
        this.authorUid = authorUid;
        this.authorNameSnapshot = authorNameSnapshot;
        this.authorProfileImageUrlSnapshot = authorProfileImageUrlSnapshot;
        this.body = body;
        this.parentCommentId = parentCommentId;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.likeCount = likeCount;
        this.isDeleted = isDeleted;
        this.pinned = pinned;
    }

    /**
     * @return The unique identifier of this comment.
     */
    public String getCommentId() {
        return commentId;
    }

    /**
     * @param commentId The unique identifier of this comment.
     */
    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    /**
     * @return The unique identifier of the associated event.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @param eventId The unique identifier of the associated event.
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * @return The unique identifier of the author.
     */
    public String getAuthorUid() {
        return authorUid;
    }

    /**
     * @param authorUid The unique identifier of the author.
     */
    public void setAuthorUid(String authorUid) {
        this.authorUid = authorUid;
    }

    /**
     * @return A snapshot of the author's name at the time of the comment.
     */
    public String getAuthorNameSnapshot() {
        return authorNameSnapshot;
    }

    /**
     * @param authorNameSnapshot Snapshot of the author's name.
     */
    public void setAuthorNameSnapshot(String authorNameSnapshot) {
        this.authorNameSnapshot = authorNameSnapshot;
    }

    /**
     * @return A snapshot of the author's profile image URL at the time of the comment, or null.
     */
    @Nullable
    public String getAuthorProfileImageUrlSnapshot() {
        return authorProfileImageUrlSnapshot;
    }

    /**
     * @param authorProfileImageUrlSnapshot Snapshot of the author's profile image URL, or null.
     */
    public void setAuthorProfileImageUrlSnapshot(@Nullable String authorProfileImageUrlSnapshot) {
        this.authorProfileImageUrlSnapshot = authorProfileImageUrlSnapshot;
    }

    /**
     * @return The text body of the comment.
     */
    public String getBody() {
        return body;
    }

    /**
     * @param body The text body of the comment.
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * @return The unique identifier of the parent comment, or null.
     */
    @Nullable
    public String getParentCommentId() {
        return parentCommentId;
    }

    /**
     * @param parentCommentId The unique identifier of the parent comment, or null.
     */
    public void setParentCommentId(@Nullable String parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    /**
     * @return The creation timestamp in epoch millis.
     */
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /**
     * @param createdAtMillis The creation timestamp in epoch millis.
     */
    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    /**
     * @return The last updated timestamp in epoch millis.
     */
    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    /**
     * @param updatedAtMillis The last updated timestamp in epoch millis.
     */
    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }

    /**
     * @return The current number of likes.
     */
    public int getLikeCount() {
        return likeCount;
    }

    /**
     * @param likeCount Sets the like count, guaranteeing it won't be negative.
     */
    public void setLikeCount(int likeCount) {
        this.likeCount = Math.max(0, likeCount);
    }

    /**
     * @return True if the comment is marked as deleted/moderated.
     */
    public boolean isDeleted() {
        return isDeleted;
    }

    /**
     * @param deleted True if the comment is marked as deleted/moderated.
     */
    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    /**
     * @return True if the comment is pinned by an organizer.
     */
    public boolean isPinned() {
        return pinned;
    }

    /**
     * @param pinned True if the comment is pinned.
     */
    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    /**
     * @return True if this is a top-level comment (has no parent ID).
     */
    public boolean isTopLevel() {
        return parentCommentId == null || parentCommentId.trim().isEmpty();
    }
}
