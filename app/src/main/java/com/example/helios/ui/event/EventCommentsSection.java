package com.example.helios.ui.event;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.model.EventComment;
import com.example.helios.model.UserProfile;
import com.example.helios.service.CommentService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.HeliosUi;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EventCommentsSection {
    interface CurrentUserListener {
        void onLoaded(@Nullable String uid);
    }

    private final Fragment fragment;
    private final String eventId;
    private final CommentService commentService;
    private final ProfileService profileService;
    private final EditText commentInput;
    private final MaterialButton postButton;
    private final CheckBox pinCommentCheckbox;
    private final TextView replyingToView;
    private final EventCommentsAdapter commentsAdapter;

    @Nullable
    private String currentUserUid;
    private boolean currentUserAdmin;
    @Nullable
    private String organizerUid;
    @Nullable
    private EventComment replyingToComment;
    @Nullable
    private ListenerRegistration topLevelCommentsRegistration;
    private final Map<String, ListenerRegistration> replyRegistrations = new HashMap<>();
    private final Map<String, String> authorProfileImageUrls = new HashMap<>();
    private final Set<String> likedCommentIds = new HashSet<>();
    private final Set<String> requestedAuthorProfileUids = new HashSet<>();

    EventCommentsSection(
            @NonNull Fragment fragment,
            @NonNull String eventId,
            @NonNull CommentService commentService,
            @NonNull ProfileService profileService,
            @NonNull RecyclerView commentsView,
            @NonNull EditText commentInput,
            @NonNull MaterialButton postButton,
            @NonNull CheckBox pinCommentCheckbox,
            @NonNull TextView replyingToView
    ) {
        this.fragment = fragment;
        this.eventId = eventId;
        this.commentService = commentService;
        this.profileService = profileService;
        this.commentInput = commentInput;
        this.postButton = postButton;
        this.pinCommentCheckbox = pinCommentCheckbox;
        this.replyingToView = replyingToView;
        this.commentsAdapter = new EventCommentsAdapter(new CommentActions());

        commentsView.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        commentsView.setNestedScrollingEnabled(false);
        commentsView.setAdapter(commentsAdapter);
        postButton.setOnClickListener(v -> onPostCommentPressed());
        clearReplyTarget();
    }

    void loadCurrentUser(@NonNull CurrentUserListener listener) {
        profileService.bootstrapCurrentUser(fragment.requireContext(), result -> {
            if (!fragment.isAdded()) {
                return;
            }
            UserProfile profile = result.getProfile();
            currentUserUid = profile.getUid();
            currentUserAdmin = profile.isAdmin();
            if (currentUserUid != null) {
                authorProfileImageUrls.put(currentUserUid, HeliosText.trimToNull(profile.getProfileImageUrl()));
                requestedAuthorProfileUids.add(currentUserUid);
                commentsAdapter.setAuthorProfileImageUrls(new HashMap<>(authorProfileImageUrls));
            }
            commentsAdapter.setCurrentUser(currentUserUid, currentUserAdmin);
            refreshLikeStates();
            clearReplyTarget();
            listener.onLoaded(currentUserUid);
        }, error -> {
            if (!fragment.isAdded()) {
                return;
            }
            HeliosUi.toast(fragment, "Comment profile unavailable: " + error.getMessage());
        });
    }

    void bindOrganizer(@Nullable String organizerUid) {
        this.organizerUid = organizerUid;
        commentsAdapter.setOrganizerUid(organizerUid);
        clearReplyTarget();
    }

    void subscribe() {
        clearListeners();
        topLevelCommentsRegistration = commentService.subscribeTopLevelComments(
                eventId,
                comments -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    commentsAdapter.setTopLevelComments(comments);
                    ensureAuthorProfileImages(comments);
                    syncReplyListeners(comments);
                    refreshLikeStates();
                },
                error -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    HeliosUi.toast(fragment, "Failed to load comments: " + error.getMessage());
                }
        );
    }

    void clear() {
        clearListeners();
    }

    @Nullable
    String getCurrentUserUid() {
        return currentUserUid;
    }

    private void onPostCommentPressed() {
        String commentText = commentInput.getText() == null ? null : commentInput.getText().toString();
        String parentId = replyingToComment != null ? replyingToComment.getCommentId() : null;
        boolean pin = pinCommentCheckbox.getVisibility() == View.VISIBLE
                && pinCommentCheckbox.isChecked()
                && parentId == null;

        postButton.setEnabled(false);

        if (pin) {
            commentService.postPinnedOrganizerComment(
                    fragment.requireContext(),
                    eventId,
                    commentText,
                    created -> resetComposer(),
                    error -> onCommentActionFailed("Post failed: " + error.getMessage())
            );
            return;
        }

        commentService.postComment(
                fragment.requireContext(),
                eventId,
                commentText,
                parentId,
                createdComment -> resetComposer(),
                error -> onCommentActionFailed("Post failed: " + error.getMessage())
        );
    }

    private void resetComposer() {
        if (!fragment.isAdded()) {
            return;
        }
        pinCommentCheckbox.setChecked(false);
        commentInput.setText("");
        clearReplyTarget();
        postButton.setEnabled(true);
    }

    private void onCommentActionFailed(@NonNull String message) {
        if (!fragment.isAdded()) {
            return;
        }
        postButton.setEnabled(true);
        HeliosUi.toast(fragment, message);
    }

    private void onReplyPressed(@NonNull EventComment comment) {
        if (!comment.isTopLevel()) {
            HeliosUi.toast(fragment, "You can only reply to top-level comments.");
            return;
        }

        replyingToComment = comment;
        pinCommentCheckbox.setVisibility(View.GONE);
        pinCommentCheckbox.setChecked(false);
        replyingToView.setText("Replying to "
                + HeliosText.nonEmptyOr(comment.getAuthorNameSnapshot(), "Anonymous")
                + " - Tap to cancel");
        replyingToView.setVisibility(View.VISIBLE);
        replyingToView.setOnClickListener(v -> clearReplyTarget());
        commentInput.requestFocus();
        commentInput.setHint("Write a reply...");
    }

    private void clearReplyTarget() {
        replyingToComment = null;
        replyingToView.setVisibility(View.GONE);
        replyingToView.setText("");
        replyingToView.setOnClickListener(null);
        commentInput.setHint("Add a comment...");

        boolean canPin = organizerUid != null
                && currentUserUid != null
                && organizerUid.equals(currentUserUid);
        pinCommentCheckbox.setVisibility(canPin ? View.VISIBLE : View.GONE);
        pinCommentCheckbox.setEnabled(canPin);
    }

    private void onLikePressed(@NonNull EventComment comment, boolean currentlyLiked) {
        String commentId = HeliosText.trimToNull(comment.getCommentId());
        if (commentId == null) {
            return;
        }

        commentService.toggleLike(fragment.requireContext(), eventId, commentId, currentlyLiked,
                willLike -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    if (willLike) {
                        likedCommentIds.add(commentId);
                    } else {
                        likedCommentIds.remove(commentId);
                    }
                    commentsAdapter.setLikedCommentIds(new HashSet<>(likedCommentIds));
                },
                error -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    HeliosUi.toast(fragment, "Like failed: " + error.getMessage());
                });
    }

    private void refreshLikeStates() {
        if (!fragment.isAdded() || currentUserUid == null) {
            return;
        }

        List<String> visibleCommentIds = commentsAdapter.getAllVisibleCommentIds();
        if (visibleCommentIds.isEmpty()) {
            likedCommentIds.clear();
            commentsAdapter.setLikedCommentIds(new HashSet<>());
            return;
        }

        commentService.loadCurrentUserLikeStates(
                fragment.requireContext(),
                eventId,
                visibleCommentIds,
                likedIds -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    likedCommentIds.clear();
                    likedCommentIds.addAll(likedIds);
                    commentsAdapter.setLikedCommentIds(new HashSet<>(likedCommentIds));
                },
                error -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    HeliosUi.toast(fragment, "Could not refresh like states: " + error.getMessage());
                }
        );
    }

    private void ensureAuthorProfileImages(@NonNull List<EventComment> comments) {
        for (EventComment comment : comments) {
            if (comment == null) {
                continue;
            }
            String authorUid = HeliosText.trimToNull(comment.getAuthorUid());
            if (authorUid == null) {
                continue;
            }
            if (authorProfileImageUrls.containsKey(authorUid) || requestedAuthorProfileUids.contains(authorUid)) {
                continue;
            }

            requestedAuthorProfileUids.add(authorUid);
            profileService.getUserProfile(
                    authorUid,
                    profile -> {
                        if (!fragment.isAdded()) {
                            return;
                        }
                        authorProfileImageUrls.put(authorUid, HeliosText.trimToNull(profile != null ? profile.getProfileImageUrl() : null));
                        commentsAdapter.setAuthorProfileImageUrls(new HashMap<>(authorProfileImageUrls));
                    },
                    error -> requestedAuthorProfileUids.remove(authorUid)
            );
        }
    }

    private void showDeleteCommentDialog(@NonNull EventComment comment) {
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle("Delete comment")
                .setMessage("Delete this comment permanently?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> deleteComment(comment))
                .show();
    }

    private void deleteComment(@NonNull EventComment comment) {
        commentService.deleteComment(
                fragment.requireContext(),
                eventId,
                comment,
                unused -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    HeliosUi.toast(fragment, "Comment deleted.");
                },
                error -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    HeliosUi.toast(fragment, "Delete failed: " + error.getMessage());
                }
        );
    }

    private void syncReplyListeners(@NonNull List<EventComment> topLevelComments) {
        Set<String> activeParentIds = new HashSet<>();
        for (EventComment comment : topLevelComments) {
            String commentId = HeliosText.trimToNull(comment.getCommentId());
            if (commentId != null) {
                activeParentIds.add(commentId);
            }
        }

        List<String> staleParentIds = new ArrayList<>();
        for (String parentId : replyRegistrations.keySet()) {
            if (!activeParentIds.contains(parentId)) {
                staleParentIds.add(parentId);
            }
        }
        for (String staleId : staleParentIds) {
            ListenerRegistration registration = replyRegistrations.remove(staleId);
            if (registration != null) {
                registration.remove();
            }
            commentsAdapter.setReplies(staleId, new ArrayList<>());
        }

        for (String parentId : activeParentIds) {
            if (replyRegistrations.containsKey(parentId)) {
                continue;
            }

            ListenerRegistration registration = commentService.subscribeReplies(
                    eventId,
                    parentId,
                    replies -> {
                        if (!fragment.isAdded()) {
                            return;
                        }
                        commentsAdapter.setReplies(parentId, replies);
                        ensureAuthorProfileImages(replies);
                        refreshLikeStates();
                    },
                    error -> {
                        if (!fragment.isAdded()) {
                            return;
                        }
                        HeliosUi.toast(fragment, "Failed to load replies: " + error.getMessage());
                    }
            );
            replyRegistrations.put(parentId, registration);
        }
    }

    private void clearListeners() {
        if (topLevelCommentsRegistration != null) {
            topLevelCommentsRegistration.remove();
            topLevelCommentsRegistration = null;
        }
        for (ListenerRegistration registration : replyRegistrations.values()) {
            registration.remove();
        }
        replyRegistrations.clear();
    }

    private final class CommentActions implements EventCommentsAdapter.CommentActionListener {
        @Override
        public void onReply(@NonNull EventComment comment) {
            onReplyPressed(comment);
        }

        @Override
        public void onLikeToggle(@NonNull EventComment comment, boolean currentlyLiked) {
            onLikePressed(comment, currentlyLiked);
        }

        @Override
        public void onDelete(@NonNull EventComment comment) {
            showDeleteCommentDialog(comment);
        }
    }
}
