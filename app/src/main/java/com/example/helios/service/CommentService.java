package com.example.helios.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.data.CommentRepository;
import com.example.helios.data.FirebaseRepository;
import com.example.helios.model.Event;
import com.example.helios.model.EventComment;
import com.example.helios.model.UserProfile;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommentService {
    private static final int MAX_COMMENT_LENGTH = 500;

    private final CommentRepository repository;
    private final ProfileService profileService;
    private final EventService eventService;

    public CommentService() {
        this(new FirebaseRepository());
    }

    public CommentService(@NonNull FirebaseRepository repository) {
        this(repository, new ProfileService(repository), new EventService(repository));
    }

    public CommentService(
            @NonNull CommentRepository repository,
            @NonNull ProfileService profileService,
            @NonNull EventService eventService
    ) {
        this.repository = repository;
        this.profileService = profileService;
        this.eventService = eventService;
    }

    @NonNull
    public ListenerRegistration subscribeTopLevelComments(
            @NonNull String eventId,
            @NonNull OnSuccessListener<List<EventComment>> onChanged,
            @NonNull OnFailureListener onError
    ) {
        return repository.subscribeComments(eventId, null, onChanged, onError);
    }

    @NonNull
    public ListenerRegistration subscribeReplies(
            @NonNull String eventId,
            @NonNull String parentCommentId,
            @NonNull OnSuccessListener<List<EventComment>> onChanged,
            @NonNull OnFailureListener onError
    ) {
        return repository.subscribeComments(eventId, parentCommentId, onChanged, onError);
    }

    public void postComment(
            @NonNull Context context,
            @NonNull String eventId,
            @Nullable String body,
            @Nullable String parentCommentId,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        String cleanedBody = cleanBody(body);
        if (cleanedBody == null) {
            onFailure.onFailure(new IllegalArgumentException("Comment text must be between 1 and 500 characters."));
            return;
        }

        profileService.bootstrapCurrentUser(context, result -> {
            UserProfile profile = result.getProfile();
            String uid = profile.getUid();
            String authorName = nonEmptyOr(profile.getDisplayNameOrFallback(), "Anonymous");
            String authorProfileImageUrl = normalize(profile.getProfileImageUrl());

            String parentId = normalize(parentCommentId);
            if (parentId == null) {
                createComment(eventId, uid, authorName, authorProfileImageUrl, cleanedBody, null, false, onSuccess, onFailure);
                return;
            }

            repository.getCommentById(eventId, parentId, parent -> {
                if (parent == null) {
                    onFailure.onFailure(new IllegalArgumentException("Cannot reply: parent comment not found."));
                    return;
                }
                if (!parent.isTopLevel()) {
                    onFailure.onFailure(new IllegalArgumentException("Only one-level replies are supported."));
                    return;
                }
                if (parent.isDeleted()) {
                    onFailure.onFailure(new IllegalArgumentException("Cannot reply to deleted comment."));
                    return;
                }
                createComment(eventId, uid, authorName, authorProfileImageUrl, cleanedBody, parentId, false, onSuccess, onFailure);
            }, onFailure);
        }, onFailure);
    }

    public void postPinnedOrganizerComment(
            @NonNull Context context,
            @NonNull String eventId,
            @Nullable String body,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        String cleanedBody = cleanBody(body);
        if (cleanedBody == null) {
            onFailure.onFailure(new IllegalArgumentException("Comment text must be between 1 and 500 characters."));
            return;
        }

        profileService.bootstrapCurrentUser(context, result -> {
            UserProfile profile = result.getProfile();
            String uid = profile.getUid();
            String authorName = nonEmptyOr(profile.getDisplayNameOrFallback(), "Organizer");
            String authorProfileImageUrl = normalize(profile.getProfileImageUrl());

            eventService.getEventById(eventId, event -> {
                if (event == null) {
                    onFailure.onFailure(new IllegalArgumentException("Event not found."));
                    return;
                }
                if (!uid.equals(event.getOrganizerUid())) {
                    onFailure.onFailure(new SecurityException("Only the organizer can pin a comment."));
                    return;
                }

                repository.getTopLevelCommentsOnce(eventId, existingComments -> {
                    long now = System.currentTimeMillis();
                    List<EventComment> toUnpin = new ArrayList<>();
                    for (EventComment c : existingComments) {
                        if (c == null || !c.isTopLevel() || !c.isPinned() || !uid.equals(c.getAuthorUid())) continue;
                        c.setPinned(false);
                        c.setUpdatedAtMillis(now);
                        toUnpin.add(c);
                    }

                    // Build the new comment object (ID assigned by repository before writing).
                    EventComment newComment = buildComment(eventId, uid, authorName, authorProfileImageUrl, cleanedBody, null, true);

                    // Atomic batch: unpin previous + create new. A failure stops the whole batch,
                    // preventing the orphaned two-pinned-comments state the previous code allowed.
                    repository.setPinnedComment(eventId, toUnpin, newComment, onSuccess, onFailure);
                }, onFailure);
            }, onFailure);
        }, onFailure);
    }

    public void toggleLike(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull String commentId,
            boolean currentlyLiked,
            @NonNull OnSuccessListener<Boolean> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId) || !isNonEmpty(commentId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId and commentId must not be empty."));
            return;
        }

        profileService.ensureSignedIn(firebaseUser -> repository.toggleCommentLike(
                eventId,
                commentId,
                firebaseUser.getUid(),
                currentlyLiked,
                onSuccess,
                onFailure
        ), onFailure);
    }

    public void getAllCommentsForAdmin(
            @NonNull OnSuccessListener<List<EventComment>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getAllComments(onSuccess, onFailure);
    }

    public void deleteComment(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull EventComment comment,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String commentId = normalize(comment.getCommentId());
        if (!isNonEmpty(eventId) || commentId == null) {
            onFailure.onFailure(new IllegalArgumentException("eventId/commentId missing."));
            return;
        }

        profileService.bootstrapCurrentUser(context, result -> {
            UserProfile profile = result.getProfile();
            String uid = profile.getUid();

            eventService.getEventById(eventId, event -> {
                boolean isOrganizer = event != null && uid.equals(event.getOrganizerUid());
                boolean canDelete = uid.equals(comment.getAuthorUid()) || profile.isAdmin() || isOrganizer;
                if (!canDelete) {
                    onFailure.onFailure(new SecurityException("You do not have permission to delete this comment."));
                    return;
                }

                if (comment.isTopLevel()) {
                    repository.deleteCommentWithReplies(eventId, commentId, onSuccess, onFailure);
                } else {
                    repository.deleteSingleCommentWithLikes(eventId, commentId, onSuccess, onFailure);
                }
            }, onFailure);
        }, onFailure);
    }

    public void loadCurrentUserLikeStates(
            @NonNull Context context,
            @NonNull String eventId,
            @NonNull List<String> commentIds,
            @NonNull OnSuccessListener<Set<String>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        if (!isNonEmpty(eventId)) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }

        Set<String> dedupedIds = new LinkedHashSet<>();
        for (String id : commentIds) {
            String normalized = normalize(id);
            if (normalized != null) {
                dedupedIds.add(normalized);
            }
        }

        if (dedupedIds.isEmpty()) {
            onSuccess.onSuccess(new LinkedHashSet<>());
            return;
        }

        profileService.ensureSignedIn(firebaseUser -> repository.getLikeStatesForComments(
                eventId,
                firebaseUser.getUid(),
                new ArrayList<>(dedupedIds),
                onSuccess,
                onFailure
        ), onFailure);
    }

    private EventComment buildComment(
            @NonNull String eventId,
            @NonNull String uid,
            @NonNull String authorName,
            @Nullable String authorProfileImageUrl,
            @NonNull String body,
            @Nullable String parentCommentId,
            boolean pinned
    ) {
        long now = System.currentTimeMillis();
        EventComment comment = new EventComment();
        comment.setEventId(eventId);
        comment.setAuthorUid(uid);
        comment.setAuthorNameSnapshot(authorName);
        comment.setAuthorProfileImageUrlSnapshot(authorProfileImageUrl);
        comment.setBody(body);
        comment.setParentCommentId(parentCommentId);
        comment.setCreatedAtMillis(now);
        comment.setUpdatedAtMillis(now);
        comment.setLikeCount(0);
        comment.setDeleted(false);
        comment.setPinned(pinned);
        return comment;
    }

    private void createComment(
            @NonNull String eventId,
            @NonNull String uid,
            @NonNull String authorName,
            @Nullable String authorProfileImageUrl,
            @NonNull String body,
            @Nullable String parentCommentId,
            boolean pinned,
            @NonNull OnSuccessListener<EventComment> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.addComment(eventId, buildComment(eventId, uid, authorName, authorProfileImageUrl, body, parentCommentId, pinned), onSuccess, onFailure);
    }

    @Nullable
    private String cleanBody(@Nullable String body) {
        String normalized = normalize(body);
        if (normalized == null || normalized.length() > MAX_COMMENT_LENGTH) {
            return null;
        }
        return normalized;
    }

    @Nullable
    private String normalize(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isNonEmpty(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    @NonNull
    private String nonEmptyOr(@Nullable String value, @NonNull String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
