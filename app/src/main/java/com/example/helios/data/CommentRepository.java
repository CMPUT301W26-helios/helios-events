package com.example.helios.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.EventComment;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Set;

/**
 * Repository interface for event comment persistence operations.
 */
public interface CommentRepository {

    void addComment(
            @NonNull String eventId,
            @NonNull EventComment comment,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getCommentById(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void updateComment(
            @NonNull String eventId,
            @NonNull EventComment comment,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getTopLevelCommentsOnce(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getAllComments(
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    @NonNull
    ListenerRegistration subscribeComments(
            @NonNull String eventId,
            @Nullable String parentCommentId,
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void toggleCommentLike(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull String uid,
            boolean currentlyLiked,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getLikeStatesForComments(
            @NonNull String eventId,
            @NonNull String uid,
            @NonNull List<String> commentIds,
            @NonNull OnSuccessListener<Set<String>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteCommentWithReplies(
            @NonNull String eventId,
            @NonNull String topLevelCommentId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteSingleCommentWithLikes(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    /**
     * Atomically unpins every comment in {@code previousPinned} and creates {@code newComment}
     * as the new pinned comment, using a single WriteBatch commit.
     * The new comment's ID and eventId are set by the implementation before writing.
     */
    void setPinnedComment(
            @NonNull String eventId,
            @NonNull List<EventComment> previousPinned,
            @NonNull EventComment newComment,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    );
}
