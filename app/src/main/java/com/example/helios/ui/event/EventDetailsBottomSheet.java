package com.example.helios.ui.event;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.EventComment;
import com.example.helios.model.UserProfile;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.CommentService;
import com.example.helios.service.EntrantEventService;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EventDetailsBottomSheet extends BottomSheetDialogFragment {

    public static final String ARG_EVENT_ID = "arg_event_id";
    public static final String ARG_HIDE_JOIN_BUTTON = "arg_hide_join_button";

    @Nullable private WaitingListEntry currentEntry = null;

    public static EventDetailsBottomSheet newInstance(@NonNull String eventId) {
        return newInstance(eventId, false);
    }

    public static EventDetailsBottomSheet newInstance(@NonNull String eventId, boolean hideJoinButton) {
        EventDetailsBottomSheet sheet = new EventDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        args.putBoolean(ARG_HIDE_JOIN_BUTTON, hideJoinButton);
        sheet.setArguments(args);
        return sheet;
    }

    private final EventService eventService = new EventService();
    private final EntrantEventService entrantEventService = new EntrantEventService();
    private final CommentService commentService = new CommentService();
    private final ProfileService profileService = new ProfileService();

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private ImageView ivPoster;
    private TextView tvName;
    private TextView tvDate;
    private TextView tvTimeStart;
    private TextView tvTimeEnd;
    private TextView tvLocation;
    private TextView tvDescription;
    private TextView tvCapacity;
    private MaterialButton btnWaitingList;

    private RecyclerView rvComments;
    private EditText etCommentBody;
    private MaterialButton btnPostComment;
    private CheckBox cbPinComment;
    private TextView tvReplyingTo;

    private String eventId;
    private boolean hideJoinButton = false;
    private Event loadedEvent;
    private boolean isCurrentlyOnWaitingList = false;

    @Nullable
    private String currentUserUid;
    private boolean currentUserAdmin = false;
    @Nullable
    private EventComment replyingToComment;

    private EventCommentsAdapter commentsAdapter;
    private ListenerRegistration topLevelCommentsRegistration;
    private final Map<String, ListenerRegistration> replyRegistrations = new HashMap<>();
    private final Set<String> likedCommentIds = new HashSet<>();

    public EventDetailsBottomSheet() {
        super(R.layout.sheet_event_details);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        View bottomSheet = getDialog().findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;

        ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.setLayoutParams(lp);
        bottomSheet.requestLayout();

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setSkipCollapsed(true);
        behavior.setFitToContents(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivPoster = view.findViewById(R.id.image_event_poster);
        tvName = view.findViewById(R.id.text_event_name);
        tvDate = view.findViewById(R.id.text_event_date);
        tvTimeStart = view.findViewById(R.id.text_event_starting_time);
        tvTimeEnd = view.findViewById(R.id.text_event_ending_time);
        tvLocation = view.findViewById(R.id.text_event_location);
        tvDescription = view.findViewById(R.id.text_event_description);
        tvCapacity = view.findViewById(R.id.text_event_capacity);
        btnWaitingList = view.findViewById(R.id.button_primary_action);

        rvComments = view.findViewById(R.id.rv_event_comments);
        etCommentBody = view.findViewById(R.id.input_comment_body);
        btnPostComment = view.findViewById(R.id.button_post_comment);
        cbPinComment = view.findViewById(R.id.cb_pin_comment);
        tvReplyingTo = view.findViewById(R.id.text_replying_to);

        View close = view.findViewById(R.id.button_close);
        if (close != null) close.setOnClickListener(v -> dismiss());

        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString(ARG_EVENT_ID);
            hideJoinButton = args.getBoolean(ARG_HIDE_JOIN_BUTTON, false);
        }

        if (eventId == null) {
            toast("Missing event id.");
            dismiss();
            return;
        }

        setupCommentsUi();

        if (btnWaitingList != null) {
            if (hideJoinButton) {
                btnWaitingList.setVisibility(View.GONE);
            } else {
                btnWaitingList.setEnabled(false);
                btnWaitingList.setOnClickListener(v -> onWaitingListButtonPressed());
            }
        }

        loadCurrentUserForComments();
        subscribeTopLevelComments();
        loadEvent();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearCommentListeners();
    }

    private void loadEvent() {
        setLoading(true);

        eventService.getEventById(eventId, event -> {
            if (!isAdded()) return;
            if (event == null) {
                toast("Event not found.");
                dismiss();
                return;
            }

            loadedEvent = event;
            bindEvent(event);
            if (commentsAdapter != null) {
                commentsAdapter.setOrganizerUid(event.getOrganizerUid());
            }

            if (!hideJoinButton) {
                refreshWaitingListState();
            } else {
                setLoading(false);
            }
            refreshCapacityCount();

        }, error -> {
            if (!isAdded()) return;
            toast("Failed to load event: " + error.getMessage());
            dismiss();
        });
    }

    private void bindEvent(@NonNull Event event) {
        if (tvName != null)
            tvName.setText(nonEmptyOr(event.getTitle(), "Untitled Event"));

        if (tvDate != null) {
            long start = event.getStartTimeMillis();
            tvDate.setText(start > 0 ? dateFormat.format(new Date(start)) : "Date TBD");
        }

        if (tvTimeStart != null) {
            long start = event.getStartTimeMillis();
            tvTimeStart.setText(start > 0 ? timeFormat.format(new Date(start)) : "TBD");
        }

        if (tvTimeEnd != null) {
            long end = event.getEndTimeMillis();
            tvTimeEnd.setText(end > 0 ? timeFormat.format(new Date(end)) : "TBD");
        }

        if (tvLocation != null) {
            String loc = nonEmptyOr(event.getLocationName(), null);
            if (loc == null) loc = nonEmptyOr(event.getAddress(), "No location");
            tvLocation.setText(loc);
        }

        if (tvDescription != null)
            tvDescription.setText(nonEmptyOr(event.getDescription(), ""));

        if (ivPoster != null) {
            String posterImageId = event.getPosterImageId();
            if (posterImageId != null && !posterImageId.trim().isEmpty()) {
                try {
                    ivPoster.setImageURI(Uri.parse(posterImageId));
                    if (ivPoster.getDrawable() == null)
                        ivPoster.setImageResource(R.drawable.elder_dance_poster_sample);
                } catch (Exception ignored) {
                    ivPoster.setImageResource(R.drawable.elder_dance_poster_sample);
                }
            } else {
                ivPoster.setImageResource(R.drawable.elder_dance_poster_sample);
            }
        }
    }

    private void setupCommentsUi() {
        if (rvComments == null) return;

        commentsAdapter = new EventCommentsAdapter(new EventCommentsAdapter.CommentActionListener() {
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
        });

        rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvComments.setNestedScrollingEnabled(false);
        rvComments.setAdapter(commentsAdapter);

        if (btnPostComment != null) {
            btnPostComment.setOnClickListener(v -> onPostCommentPressed());
        }

        clearReplyTarget();
    }

    private void loadCurrentUserForComments() {
        profileService.bootstrapCurrentUser(requireContext(), result -> {
            if (!isAdded()) return;
            UserProfile profile = result.getProfile();
            currentUserUid = profile.getUid();
            currentUserAdmin = profile.isAdmin();

            if (commentsAdapter != null) {
                commentsAdapter.setCurrentUser(currentUserUid, currentUserAdmin);
            }
            refreshLikeStates();
        }, error -> {
            if (!isAdded()) return;
            toast("Comment profile unavailable: " + error.getMessage());
        });
    }

    private void subscribeTopLevelComments() {
        if (eventId == null) return;

        if (topLevelCommentsRegistration != null) {
            topLevelCommentsRegistration.remove();
            topLevelCommentsRegistration = null;
        }

        topLevelCommentsRegistration = commentService.subscribeTopLevelComments(eventId,
                comments -> {
                    if (!isAdded() || commentsAdapter == null) return;
                    commentsAdapter.setTopLevelComments(comments);
                    syncReplyListeners(comments);
                    refreshLikeStates();
                },
                error -> {
                    if (!isAdded()) return;
                    toast("Failed to load comments: " + error.getMessage());
                });
    }

    private void syncReplyListeners(@NonNull List<EventComment> topLevelComments) {
        Set<String> activeParentIds = new HashSet<>();
        for (EventComment comment : topLevelComments) {
            String commentId = trimToNull(comment.getCommentId());
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
            if (commentsAdapter != null) {
                commentsAdapter.setReplies(staleId, new ArrayList<>());
            }
        }

        for (String parentId : activeParentIds) {
            if (replyRegistrations.containsKey(parentId)) continue;

            ListenerRegistration registration = commentService.subscribeReplies(eventId, parentId,
                    replies -> {
                        if (!isAdded() || commentsAdapter == null) return;
                        commentsAdapter.setReplies(parentId, replies);
                        refreshLikeStates();
                    },
                    error -> {
                        if (!isAdded()) return;
                        toast("Failed to load replies: " + error.getMessage());
                    });

            replyRegistrations.put(parentId, registration);
        }
    }

    private void onPostCommentPressed() {
        if (eventId == null || etCommentBody == null || btnPostComment == null) return;

        String commentText = etCommentBody.getText() == null ? null : etCommentBody.getText().toString();
        String parentId = replyingToComment != null ? replyingToComment.getCommentId() : null;
        boolean pin = cbPinComment != null
                && cbPinComment.getVisibility() == View.VISIBLE
                && cbPinComment.isChecked()
                && parentId == null;

        btnPostComment.setEnabled(false);

        if (pin) {
            commentService.postPinnedOrganizerComment(
                    requireContext(),
                    eventId,
                    commentText,
                    created -> {
                        if (!isAdded()) return;
                        if (cbPinComment != null) cbPinComment.setChecked(false);
                        etCommentBody.setText("");
                        clearReplyTarget();
                        btnPostComment.setEnabled(true);
                    },
                    error -> {
                        if (!isAdded()) return;
                        btnPostComment.setEnabled(true);
                        toast("Post failed: " + error.getMessage());
                    }
            );
        } else {
            commentService.postComment(
                    requireContext(),
                    eventId,
                    commentText,
                    parentId,
                    createdComment -> {
                        if (!isAdded()) return;
                        etCommentBody.setText("");
                        clearReplyTarget();
                        btnPostComment.setEnabled(true);
                    },
                    error -> {
                        if (!isAdded()) return;
                        btnPostComment.setEnabled(true);
                        toast("Post failed: " + error.getMessage());
                    }
            );
        }
    }

    private void onReplyPressed(@NonNull EventComment comment) {
        if (!comment.isTopLevel()) {
            toast("You can only reply to top-level comments.");
            return;
        }

        replyingToComment = comment;
        if (cbPinComment != null) {
            cbPinComment.setVisibility(View.GONE);
            cbPinComment.setChecked(false);
        }
        if (tvReplyingTo != null) {
            String label = nonEmptyOr(comment.getAuthorNameSnapshot(), "Anonymous");
            tvReplyingTo.setText("Replying to " + label + " - Tap to cancel");
            tvReplyingTo.setVisibility(View.VISIBLE);
            tvReplyingTo.setOnClickListener(v -> clearReplyTarget());
        }

        if (etCommentBody != null) {
            etCommentBody.requestFocus();
            etCommentBody.setHint("Write a reply...");
        }
    }

    private void clearReplyTarget() {
        replyingToComment = null;

        if (tvReplyingTo != null) {
            tvReplyingTo.setVisibility(View.GONE);
            tvReplyingTo.setText("");
            tvReplyingTo.setOnClickListener(null);
        }

        if (etCommentBody != null) {
            etCommentBody.setHint("Add a comment...");
        }

        if (cbPinComment != null) {
            boolean canPin = loadedEvent != null
                    && currentUserUid != null
                    && currentUserUid.equals(loadedEvent.getOrganizerUid());
            cbPinComment.setVisibility(canPin ? View.VISIBLE : View.GONE);
            cbPinComment.setEnabled(canPin);
        }
    }

    private void onLikePressed(@NonNull EventComment comment, boolean currentlyLiked) {
        String commentId = trimToNull(comment.getCommentId());
        if (eventId == null || commentId == null) return;

        commentService.toggleLike(requireContext(), eventId, commentId, currentlyLiked,
                willLike -> {
                    if (!isAdded()) return;
                    if (willLike) {
                        likedCommentIds.add(commentId);
                    } else {
                        likedCommentIds.remove(commentId);
                    }
                    if (commentsAdapter != null) {
                        commentsAdapter.setLikedCommentIds(new HashSet<>(likedCommentIds));
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    toast("Like failed: " + error.getMessage());
                });
    }

    private void refreshLikeStates() {
        if (!isAdded() || commentsAdapter == null || eventId == null || currentUserUid == null) return;

        List<String> visibleCommentIds = commentsAdapter.getAllVisibleCommentIds();
        if (visibleCommentIds.isEmpty()) {
            likedCommentIds.clear();
            commentsAdapter.setLikedCommentIds(new HashSet<>());
            return;
        }

        commentService.loadCurrentUserLikeStates(requireContext(), eventId, visibleCommentIds,
                likedIds -> {
                    if (!isAdded() || commentsAdapter == null) return;
                    likedCommentIds.clear();
                    likedCommentIds.addAll(likedIds);
                    commentsAdapter.setLikedCommentIds(new HashSet<>(likedCommentIds));
                },
                error -> {
                    if (!isAdded()) return;
                    toast("Could not refresh like states: " + error.getMessage());
                });
    }

    private void showDeleteCommentDialog(@NonNull EventComment comment) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete comment")
                .setMessage("Delete this comment permanently?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> deleteComment(comment))
                .show();
    }

    private void deleteComment(@NonNull EventComment comment) {
        if (eventId == null) return;

        commentService.deleteComment(requireContext(), eventId, comment,
                unused -> {
                    if (!isAdded()) return;
                    toast("Comment deleted.");
                },
                error -> {
                    if (!isAdded()) return;
                    toast("Delete failed: " + error.getMessage());
                });
    }

    private void clearCommentListeners() {
        if (topLevelCommentsRegistration != null) {
            topLevelCommentsRegistration.remove();
            topLevelCommentsRegistration = null;
        }

        for (ListenerRegistration registration : replyRegistrations.values()) {
            registration.remove();
        }
        replyRegistrations.clear();
    }

    private void refreshWaitingListState() {
        entrantEventService.getCurrentUserWaitingListEntry(requireContext(), eventId, entry -> {
            if (!isAdded()) return;
            currentEntry = entry;

            isCurrentlyOnWaitingList = entry != null
                    && entry.getStatus() != null
                    && entry.getStatus() != WaitingListStatus.CANCELLED
                    && entry.getStatus() != WaitingListStatus.NOT_SELECTED
                    && entry.getStatus() != WaitingListStatus.DECLINED;

            updateWaitingListButton();
            setLoading(false);

        }, error -> {
            if (!isAdded()) return;
            updateWaitingListButton();
            setLoading(false);
        });
    }

    private void updateWaitingListButton() {
        if (btnWaitingList == null || hideJoinButton) return;

        if (currentEntry != null && currentEntry.getStatus() == WaitingListStatus.INVITED) {
            btnWaitingList.setVisibility(View.GONE);
            showAcceptDeclineButtons();
            return;
        }

        btnWaitingList.setVisibility(View.VISIBLE);
        btnWaitingList.setEnabled(true);

        if (currentEntry != null && currentEntry.getStatus() == WaitingListStatus.ACCEPTED) {
            btnWaitingList.setText("✅ Invitation Accepted");
            btnWaitingList.setEnabled(false);
        } else if (currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.NOT_SELECTED) {
            btnWaitingList.setText("Not selected in lottery");
            btnWaitingList.setEnabled(false);
        } else if (currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.DECLINED) {
            btnWaitingList.setText("Invitation Declined");
            btnWaitingList.setEnabled(false);
        } else if (isCurrentlyOnWaitingList) {
            btnWaitingList.setText("Leave Waiting List");
        } else {
            btnWaitingList.setText("Join Waiting List");
        }
    }

    private void showAcceptDeclineButtons() {
        if (getView() == null) return;
        View container = getView().findViewById(R.id.layout_action_buttons);
        if (container == null) return;
        container.setVisibility(View.VISIBLE);

        MaterialButton btnAccept = getView().findViewById(R.id.button_accept_invitation);
        MaterialButton btnDecline = getView().findViewById(R.id.button_decline_invitation);

        if (btnAccept != null) btnAccept.setOnClickListener(v -> acceptInvitation());
        if (btnDecline != null) btnDecline.setOnClickListener(v -> declineInvitation());
    }

    private void acceptInvitation() {
        if (currentEntry == null) return;
        currentEntry.setStatus(WaitingListStatus.ACCEPTED);
        currentEntry.setRespondedAtMillis(System.currentTimeMillis());
        saveEntryResponse("Invitation accepted!");
    }

    private void declineInvitation() {
        if (currentEntry == null) return;
        currentEntry.setStatus(WaitingListStatus.DECLINED);
        currentEntry.setRespondedAtMillis(System.currentTimeMillis());
        saveEntryResponse("Invitation declined.");
    }

    private void saveEntryResponse(String successMsg) {
        if (currentEntry == null) return;
        entrantEventService.updateEntry(requireContext(), eventId, currentEntry,
                unused -> {
                    if (!isAdded()) return;
                    updateWaitingListButton();
                    toast(successMsg);
                },
                error -> {
                    if (!isAdded()) return;
                    toast("Failed to save response: " + error.getMessage());
                });
    }

    private void refreshCapacityCount() {
        if (loadedEvent == null || tvCapacity == null) return;
        entrantEventService.getFilledSlotsCount(eventId, filled -> {
            if (!isAdded()) return;
            tvCapacity.setText("Waiting list capacity: "
                    + filled + " / " + loadedEvent.getCapacity());
        }, error -> {
            if (!isAdded()) return;
            tvCapacity.setText("Waiting list capacity: ? / " + loadedEvent.getCapacity());
        });
    }

    private void showJoinWaitingListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        builder.setView(R.layout.dialog_waiting_list_confirm)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Confirm", (dialog, which) -> joinWaitingListConfirmed())
                .show();
    }

    private void joinWaitingListConfirmed() {
        if (btnWaitingList != null) btnWaitingList.setEnabled(false);
        entrantEventService.joinWaitingList(requireContext(), eventId,
                unused -> {
                    if (!isAdded()) return;
                    isCurrentlyOnWaitingList = true;
                    updateWaitingListButton();
                    refreshCapacityCount();
                    toast("Joined waiting list.");
                },
                error -> {
                    if (!isAdded()) return;
                    updateWaitingListButton();
                    toast("Join failed: " + error.getMessage());
                });
    }

    private void leaveWaitingList() {
        if (btnWaitingList != null) btnWaitingList.setEnabled(false);
        entrantEventService.leaveWaitingList(requireContext(), eventId,
                unused -> {
                    if (!isAdded()) return;
                    isCurrentlyOnWaitingList = false;
                    updateWaitingListButton();
                    refreshCapacityCount();
                    toast("Left waiting list.");
                },
                error -> {
                    if (!isAdded()) return;
                    updateWaitingListButton();
                    toast("Leave failed: " + error.getMessage());
                });
    }

    private void onWaitingListButtonPressed() {
        if (isCurrentlyOnWaitingList) {
            leaveWaitingList();
        } else {
            showJoinWaitingListDialog();
        }
    }

    private void setLoading(boolean loading) {
        if (tvName != null && loading) tvName.setText("Loading...");
        if (btnWaitingList != null && !hideJoinButton) btnWaitingList.setEnabled(!loading);
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nonEmptyOr(String value, String fallback) {
        if (value == null) return fallback;
        String t = value.trim();
        return t.isEmpty() ? fallback : t;
    }
}
