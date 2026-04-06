package com.example.helios.ui.event;

import android.Manifest;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.EntrantEventService;
import com.example.helios.service.EventService;
import com.example.helios.service.WaitingListService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.common.HeliosChipFactory;
import com.example.helios.ui.common.HeliosLocation;
import com.example.helios.ui.common.HeliosText;
import com.example.helios.ui.common.HeliosUi;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventDetailsBottomSheet extends BottomSheetDialogFragment {

    public static final String ARG_EVENT_ID = "arg_event_id";
    public static final String ARG_HIDE_JOIN_BUTTON = "arg_hide_join_button";
    public static final String RESULT_INVITATION_RESPONSE = "event_invitation_response";
    public static final String RESULT_EVENT_ID = "result_event_id";
    public static final String RESULT_STATUS = "result_status";

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

    private EventService eventService;
    private EntrantEventService entrantEventService;
    private WaitingListService waitingListService;

    private ImageView ivPoster;
    private TextView tvName;
    private TextView tvDate;
    private TextView tvTimeStart;
    private TextView tvTimeEnd;
    private TextView tvRegistrationStartDate;
    private TextView tvRegistrationEndDate;
    private TextView tvRegistrationEndTime;
    private ChipGroup cgEventTags;
    private TextView tvLocation;
    private TextView tvDescription;
    private View descriptionCard;
    private TextView tvCapacity;
    private TextView tvWaitlistCount;
    private TextView tvLotteryGuidelinesLabel;
    private TextView tvLotteryGuidelines;
    private View lotteryGuidelinesCard;
    private MaterialButton btnWaitingList;
    private View cardPoster;
    private ImageView ivMaximizeIcon;
    private boolean isImageExpanded = false;

    private String eventId;
    private boolean hideJoinButton = false;
    private Event loadedEvent;
    private boolean isCurrentlyOnWaitingList = false;

    @Nullable
    private String currentUserUid;
    @Nullable
    private EventCommentsSection commentsSection;
    @Nullable
    private FusedLocationProviderClient locationClient;
    @Nullable
    private Runnable pendingLocationAction;
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handleLocationPermissionResult);
    private final ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> handleLocationSettingsResolutionResult(result.getResultCode() == Activity.RESULT_OK));

    public EventDetailsBottomSheet() {
        super(R.layout.sheet_event_details);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        entrantEventService = application.getEntrantEventService();
        waitingListService = application.getWaitingListService();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        View bottomSheet = getDialog().findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;

        int sheetSurfaceColor = MaterialColors.getColor(
                bottomSheet,
                com.google.android.material.R.attr.colorSurface
        );
        bottomSheet.setBackgroundTintList(ColorStateList.valueOf(sheetSurfaceColor));

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
        tvRegistrationStartDate = view.findViewById(R.id.text_registration_start_date);
        tvRegistrationEndDate = view.findViewById(R.id.text_registration_end_date);
        tvRegistrationEndTime = view.findViewById(R.id.text_registration_end_time);
        cgEventTags = view.findViewById(R.id.cg_event_tags);
        tvLocation = view.findViewById(R.id.text_event_location);
        tvDescription = view.findViewById(R.id.text_event_description);
        descriptionCard = view.findViewById(R.id.description_card);
        tvCapacity = view.findViewById(R.id.text_event_capacity);
        tvWaitlistCount = view.findViewById(R.id.tvWaitlistCount);
        tvLotteryGuidelinesLabel = view.findViewById(R.id.tvLotteryGuidelinesLabel);
        tvLotteryGuidelines = view.findViewById(R.id.tvLotteryGuidelines);
        lotteryGuidelinesCard = view.findViewById(R.id.lottery_guidelines_card);
        btnWaitingList = view.findViewById(R.id.button_primary_action);

        View close = view.findViewById(R.id.button_close);
        if (close != null) close.setOnClickListener(v -> dismiss());

        cardPoster = view.findViewById(R.id.card_event_poster);
        ivMaximizeIcon = view.findViewById(R.id.iv_maximize_icon);
        View btnMaximize = view.findViewById(R.id.btn_maximize_poster);

        if (ivPoster != null) {
            ivPoster.setOnClickListener(v -> toggleImageExpansion());
        }
        if (btnMaximize != null) {
            btnMaximize.setOnClickListener(v -> toggleImageExpansion());
        }

        Bundle args = getArguments();
        if (args != null) {
            eventId = EventNavArgs.getEventId(args);
            hideJoinButton = args.getBoolean(ARG_HIDE_JOIN_BUTTON, false);
        }

        if (eventId == null) {
            toast("Missing event id.");
            dismiss();
            return;
        }

        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        commentsSection = new EventCommentsSection(
                this,
                eventId,
                HeliosApplication.from(requireContext()).getCommentService(),
                HeliosApplication.from(requireContext()).getProfileService(),
                view.findViewById(R.id.rv_event_comments),
                view.findViewById(R.id.input_comment_body),
                view.findViewById(R.id.button_post_comment),
                view.findViewById(R.id.cb_pin_comment),
                view.findViewById(R.id.text_replying_to)
        );
        commentsSection.loadCurrentUser(uid -> {
            currentUserUid = uid;
            updateWaitingListButton();
        });
        commentsSection.subscribe();

        if (btnWaitingList != null) {
            if (hideJoinButton) {
                btnWaitingList.setVisibility(View.GONE);
            } else {
                btnWaitingList.setEnabled(false);
                btnWaitingList.setOnClickListener(v -> onWaitingListButtonPressed());
            }
        }

        loadEvent();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (commentsSection != null) {
            commentsSection.clear();
            commentsSection = null;
        }
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
            if (commentsSection != null) {
                commentsSection.bindOrganizer(event.getOrganizerUid());
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
            tvName.setText(EventUiFormatter.getTitle(event));

        if (tvDate != null) {
            tvDate.setText(EventUiFormatter.getDateLabel(event));
        }

        if (tvTimeStart != null) {
            tvTimeStart.setText(EventUiFormatter.getStartTimeLabel(event));
        }

        if (tvTimeEnd != null) {
            tvTimeEnd.setText(EventUiFormatter.getEndTimeLabel(event));
        }

        if (tvRegistrationStartDate != null) {
            tvRegistrationStartDate.setText(EventUiFormatter.getRegistrationStartDateLabel(event));
        }

        if (tvRegistrationEndDate != null) {
            tvRegistrationEndDate.setText(EventUiFormatter.getRegistrationEndDateLabel(event));
        }

        if (tvRegistrationEndTime != null) {
            tvRegistrationEndTime.setText(EventUiFormatter.getRegistrationEndTimeLabel(event));
        }

        if (tvLocation != null) {
            tvLocation.setText(EventUiFormatter.getLocationDetailLabel(event));
        }

        if (tvDescription != null) {
            String description = event.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                tvDescription.setText(description.trim());
                if (descriptionCard != null) {
                    descriptionCard.setVisibility(View.VISIBLE);
                }
            } else if (descriptionCard != null) {
                descriptionCard.setVisibility(View.GONE);
            }
        }

        if (cgEventTags != null) {
            cgEventTags.removeAllViews();
            List<String> displayTags = EventUiFormatter.getDisplayTags(event);
            for (String tag : displayTags) {
                cgEventTags.addView(HeliosChipFactory.createAssistChip(
                        requireContext(),
                        "#" + tag
                ));
            }
            cgEventTags.setVisibility(displayTags.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (tvLotteryGuidelinesLabel != null && tvLotteryGuidelines != null) {
            String guidelines = event.getLotteryGuidelines();
            if (guidelines != null && !guidelines.trim().isEmpty()) {
                if (lotteryGuidelinesCard != null) {
                    lotteryGuidelinesCard.setVisibility(View.VISIBLE);
                }
                tvLotteryGuidelinesLabel.setVisibility(View.VISIBLE);
                tvLotteryGuidelines.setVisibility(View.VISIBLE);
                tvLotteryGuidelines.setText(guidelines);
            } else {
                if (lotteryGuidelinesCard != null) {
                    lotteryGuidelinesCard.setVisibility(View.GONE);
                }
                tvLotteryGuidelinesLabel.setVisibility(View.GONE);
                tvLotteryGuidelines.setVisibility(View.GONE);
            }
        }

        if (ivPoster != null) {
            String posterImageId = event.getPosterImageId();
            if (posterImageId != null && !posterImageId.trim().isEmpty()) {
                Glide.with(this)
                        .load(posterImageId)
                        .fitCenter()
                        .placeholder(R.drawable.placeholder_event)
                        .error(R.drawable.placeholder_event)
                        .into(ivPoster);
            } else {
                ivPoster.setImageResource(R.drawable.placeholder_event);
            }
        }
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

        if (currentUserUid != null && loadedEvent != null) {
            if (loadedEvent.isPendingCoOrganizer(currentUserUid)) {
                btnWaitingList.setVisibility(View.GONE);
                showActionButtons(
                        "Accept Co-organizer Invite",
                        "Decline Co-organizer Invite",
                        this::acceptCoOrganizerInvite,
                        this::declineCoOrganizerInvite
                );
                return;
            }

            if (isCurrentUserOrganizerForEvent()) {
                hideActionButtons();
                btnWaitingList.setVisibility(View.VISIBLE);
                btnWaitingList.setText("You are organizing this event");
                btnWaitingList.setEnabled(false);
                return;
            }
        }

        hideActionButtons();

        if (currentEntry != null && currentEntry.getStatus() == WaitingListStatus.INVITED) {
            btnWaitingList.setVisibility(View.GONE);
            showActionButtons(
                    "Accept Invitation",
                    "Decline Invitation",
                    this::acceptInvitation,
                    this::declineInvitation
            );
            return;
        }

        btnWaitingList.setVisibility(View.VISIBLE);
        btnWaitingList.setEnabled(true);

        if (loadedEvent != null
                && loadedEvent.isPrivateEvent()
                && currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.ACCEPTED) {
            btnWaitingList.setText("Leave Private Event");
            return;
        } else if (currentEntry != null && currentEntry.getStatus() == WaitingListStatus.ACCEPTED) {
            btnWaitingList.setText("Invitation accepted");
            btnWaitingList.setEnabled(false);
        } else if (loadedEvent != null
                && loadedEvent.isPrivateEvent()
                && currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.CANCELLED) {
            btnWaitingList.setText("You left this private event");
            btnWaitingList.setEnabled(false);
        } else if (currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.NOT_SELECTED) {
            btnWaitingList.setText("Not selected in lottery");
            btnWaitingList.setEnabled(false);
        } else if (currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.DECLINED) {
            btnWaitingList.setText("Invitation Declined");
            btnWaitingList.setEnabled(false);
        } else if (loadedEvent != null && loadedEvent.isPrivateEvent()) {
            btnWaitingList.setText("Private event invite unavailable");
            btnWaitingList.setEnabled(false);
        } else if (isCurrentlyOnWaitingList) {
            btnWaitingList.setText("Leave Waiting List");
        } else {
            btnWaitingList.setText("Join Waiting List");
        }
    }

    private void showActionButtons(
            @NonNull String acceptText,
            @NonNull String declineText,
            @NonNull Runnable acceptAction,
            @NonNull Runnable declineAction
    ) {
        if (getView() == null) return;
        View container = getView().findViewById(R.id.layout_action_buttons);
        if (container == null) return;
        container.setVisibility(View.VISIBLE);

        MaterialButton btnAccept = getView().findViewById(R.id.button_accept_invitation);
        MaterialButton btnDecline = getView().findViewById(R.id.button_decline_invitation);

        if (btnAccept != null) {
            btnAccept.setText(acceptText);
            btnAccept.setOnClickListener(v -> acceptAction.run());
        }
        if (btnDecline != null) {
            btnDecline.setText(declineText);
            btnDecline.setOnClickListener(v -> declineAction.run());
        }
    }

    private void hideActionButtons() {
        if (getView() == null) return;
        View container = getView().findViewById(R.id.layout_action_buttons);
        if (container != null) {
            container.setVisibility(View.GONE);
        }
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
        if (loadedEvent != null && loadedEvent.getEventId() != null) {
            waitingListService.autoInviteReplacement(loadedEvent.getEventId(),
                    invited -> {}, error -> {});
        }
    }

    private void saveEntryResponse(String successMsg) {
        if (currentEntry == null) return;
        entrantEventService.updateEntry(requireContext(), eventId, currentEntry,
                unused -> {
                    if (!isAdded()) return;
                    updateWaitingListButton();
                    refreshCapacityCount();
                    notifyInvitationResponseChanged();
                    toast(successMsg);
                },
                error -> {
                    if (!isAdded()) return;
                    toast("Failed to save response: " + error.getMessage());
                });
    }

    private void notifyEntryStatusChanged() {
        if (currentEntry == null) {
            return;
        }
        notifyWaitingListStatusChanged(currentEntry.getStatus());
    }

    private void notifyWaitingListStatusChanged(@Nullable WaitingListStatus status) {
        if (eventId == null || status == null) {
            return;
        }
        Bundle result = new Bundle();
        result.putString(RESULT_EVENT_ID, eventId);
        result.putString(RESULT_STATUS, status.name());
        getParentFragmentManager().setFragmentResult(RESULT_INVITATION_RESPONSE, result);
    }

    private void acceptCoOrganizerInvite() {
        if (loadedEvent == null || currentUserUid == null) return;

        List<String> pending = loadedEvent.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(currentUserUid)) {
            toast("Co-organizer invite no longer exists.");
            updateWaitingListButton();
            return;
        }

        List<String> coOrganizers = loadedEvent.getCoOrganizerUids();
        if (coOrganizers == null) coOrganizers = new ArrayList<>();
        if (!coOrganizers.contains(currentUserUid)) {
            coOrganizers.add(currentUserUid);
        }

        loadedEvent.setPendingCoOrganizerUids(pending);
        loadedEvent.setCoOrganizerUids(coOrganizers);

        eventService.saveEvent(loadedEvent, unused -> {
            if (!isAdded()) return;
            waitingListService.removeEntry(eventId, currentUserUid, unused2 -> {
                if (!isAdded()) return;
                currentEntry = null;
                isCurrentlyOnWaitingList = false;
                updateWaitingListButton();
                refreshCapacityCount();
                toast("Co-organizer invite accepted!");
            }, error -> {
                if (!isAdded()) return;
                currentEntry = null;
                isCurrentlyOnWaitingList = false;
                updateWaitingListButton();
                refreshCapacityCount();
                toast("Accepted, but could not remove waiting list entry: " + error.getMessage());
            });
        }, error -> {
            if (!isAdded()) return;
            toast("Failed to accept co-organizer invite: " + error.getMessage());
        });
    }

    private void declineCoOrganizerInvite() {
        if (loadedEvent == null || currentUserUid == null) return;

        List<String> pending = loadedEvent.getPendingCoOrganizerUids();
        if (pending == null || !pending.remove(currentUserUid)) {
            toast("Co-organizer invite no longer exists.");
            updateWaitingListButton();
            return;
        }

        loadedEvent.setPendingCoOrganizerUids(pending);
        eventService.saveEvent(loadedEvent, unused -> {
            if (!isAdded()) return;
            updateWaitingListButton();
            toast("Co-organizer invite declined.");
        }, error -> {
            if (!isAdded()) return;
            toast("Failed to decline co-organizer invite: " + error.getMessage());
        });
    }

    private void refreshCapacityCount() {
        if (loadedEvent == null || tvCapacity == null) return;
        entrantEventService.getFilledSlotsCount(eventId, filled -> {
            if (!isAdded()) return;
            tvCapacity.setText(filled + " of " + loadedEvent.getCapacity() + " spots filled");
            if (tvWaitlistCount != null) {
                Integer waitlistLimit = loadedEvent.getWaitlistLimit();
                tvWaitlistCount.setVisibility(View.VISIBLE);
                if (waitlistLimit != null) {
                    tvWaitlistCount.setText("Waitlist: " + filled + " / " + waitlistLimit);
                } else {
                    tvWaitlistCount.setText("Waitlist: " + filled);
                }
            }
        }, error -> {
            if (!isAdded()) return;
            tvCapacity.setText("Capacity unavailable");
            if (tvWaitlistCount != null) {
                tvWaitlistCount.setVisibility(View.GONE);
            }
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
        if (loadedEvent != null && loadedEvent.isGeolocationRequired()) {
            requestRequiredLocationThenJoin();
            return;
        }
        performJoinWaitingList(null, null);
    }

    private void requestRequiredLocationThenJoin() {
        if (!isAdded()) {
            return;
        }
        pendingLocationAction = this::fetchRequiredLocationThenJoin;
        requestLocationAccessAndServices();
    }

    private void requestLocationAccessAndServices() {
        if (!isAdded()) {
            return;
        }
        if (!HeliosLocation.hasAnyLocationPermission(requireContext())) {
            locationPermissionLauncher.launch(HeliosLocation.LOCATION_PERMISSIONS);
            return;
        }
        LocationServices.getSettingsClient(requireContext())
                .checkLocationSettings(HeliosLocation.createLocationSettingsRequest(requireContext()))
                .addOnSuccessListener(unused -> runPendingLocationAction())
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (error instanceof ResolvableApiException) {
                        locationSettingsLauncher.launch(new IntentSenderRequest.Builder(
                                ((ResolvableApiException) error).getResolution()
                        ).build());
                        return;
                    }
                    clearPendingLocationAction();
                    updateWaitingListButton();
                    showLocationRequiredDialog(HeliosLocation.buildLocationServicesDisabledMessage("join this event"));
                });
    }

    private void fetchRequiredLocationThenJoin() {
        if (!isAdded()) {
            return;
        }
        if (locationClient == null) {
            locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        }
        try {
            CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
            locationClient.getCurrentLocation(
                            HeliosLocation.createCurrentLocationRequest(requireContext()),
                            cancellationTokenSource.getToken()
                    )
                    .addOnSuccessListener(location -> {
                        if (!isAdded()) return;
                        if (location != null) {
                            performJoinWaitingList(location.getLatitude(), location.getLongitude());
                            return;
                        }
                        fetchLastKnownLocationThenJoin();
                    })
                    .addOnFailureListener(error -> {
                        if (!isAdded()) return;
                        fetchLastKnownLocationThenJoin();
                    });
        } catch (SecurityException e) {
            onJoinWaitingListFailed(e);
        }
    }

    private void fetchLastKnownLocationThenJoin() {
        if (!isAdded() || locationClient == null) {
            return;
        }
        try {
            locationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (!isAdded()) return;
                        if (location != null) {
                            performJoinWaitingList(location.getLatitude(), location.getLongitude());
                            return;
                        }
                        onJoinWaitingListFailed(new IllegalStateException(
                                HeliosLocation.buildLocationUnavailableMessage("join this event")
                        ));
                    })
                    .addOnFailureListener(error -> {
                        if (!isAdded()) return;
                        onJoinWaitingListFailed(new IllegalStateException(
                                HeliosLocation.buildLocationUnavailableMessage("join this event"),
                                error
                        ));
                    });
        } catch (SecurityException e) {
            onJoinWaitingListFailed(e);
        }
    }

    private void performJoinWaitingList(@Nullable Double latitude, @Nullable Double longitude) {
        entrantEventService.joinWaitingList(eventId, latitude, longitude,
                unused -> {
                    if (!isAdded()) return;
                    isCurrentlyOnWaitingList = true;
                    updateWaitingListButton();
                    refreshCapacityCount();
                    toast("Joined waiting list.");
                },
                this::onJoinWaitingListFailed);
    }

    private void handleLocationPermissionResult(@NonNull Map<String, Boolean> grantResults) {
        if (!isAdded()) {
            return;
        }
        if (HeliosLocation.hasAnyLocationPermission(requireContext())) {
            requestLocationAccessAndServices();
            return;
        }
        clearPendingLocationAction();
        updateWaitingListButton();
        showLocationRequiredDialog(HeliosLocation.buildPermissionDeniedMessage("join this event"));
    }

    private void handleLocationSettingsResolutionResult(boolean enabled) {
        if (!isAdded()) {
            return;
        }
        if (enabled) {
            requestLocationAccessAndServices();
            return;
        }
        clearPendingLocationAction();
        updateWaitingListButton();
        showLocationRequiredDialog(HeliosLocation.buildLocationServicesDisabledMessage("join this event"));
    }

    private void showLocationRequiredDialog(@NonNull String baseMessage) {
        if (!isAdded()) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Location Required")
                .setMessage(buildLocationRequiredMessage(baseMessage))
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @NonNull
    private String buildLocationRequiredMessage(@NonNull String baseMessage) {
        if (loadedEvent == null) {
            return baseMessage;
        }
        String geofenceSummary = EventUiFormatter.getGeofenceSummary(loadedEvent);
        if (geofenceSummary == null) {
            return baseMessage;
        }
        return baseMessage + " This event keeps entrants within the configured "
                + geofenceSummary.toLowerCase() + '.';
    }

    private void onJoinWaitingListFailed(@NonNull Exception error) {
        if (!isAdded()) return;
        clearPendingLocationAction();
        updateWaitingListButton();
        toast("Join failed: " + error.getMessage());
    }

    private void runPendingLocationAction() {
        Runnable action = pendingLocationAction;
        pendingLocationAction = null;
        if (action != null) {
            action.run();
        }
    }

    private void clearPendingLocationAction() {
        pendingLocationAction = null;
    }

    private void leaveWaitingList() {
        if (btnWaitingList != null) btnWaitingList.setEnabled(false);
        boolean leavingPrivateEvent = loadedEvent != null
                && loadedEvent.isPrivateEvent()
                && currentEntry != null
                && currentEntry.getStatus() == WaitingListStatus.ACCEPTED;
        entrantEventService.leaveWaitingList(requireContext(), eventId,
                unused -> {
                    if (!isAdded()) return;
                    if (currentEntry != null) {
                        currentEntry.setStatus(WaitingListStatus.CANCELLED);
                        currentEntry.setCancelledAtMillis(System.currentTimeMillis());
                    }
                    isCurrentlyOnWaitingList = false;
                    updateWaitingListButton();
                    refreshCapacityCount();
                    notifyEntryStatusChanged();
                    toast(leavingPrivateEvent ? "Left private event." : "Left waiting list.");
                },
                error -> {
                    if (!isAdded()) return;
                    updateWaitingListButton();
                    toast("Leave failed: " + error.getMessage());
                });
    }

    private void onWaitingListButtonPressed() {
        if (isCurrentUserOrganizerForEvent()) {
            updateWaitingListButton();
            return;
        }
        if (isCurrentlyOnWaitingList) {
            leaveWaitingList();
        } else {
            showJoinWaitingListDialog();
        }
    }

    private void setLoading(boolean loading) {
        if (tvName != null && loading) tvName.setText("Loading...");
        if (btnWaitingList == null || hideJoinButton) {
            return;
        }
        if (loading) {
            btnWaitingList.setEnabled(false);
            return;
        }
        updateWaitingListButton();
    }

    private void notifyInvitationResponseChanged() {
        notifyEntryStatusChanged();
    }

    private void toast(String msg) {
        HeliosUi.toast(this, msg);
    }

    private boolean isCurrentUserOrganizerForEvent() {
        return currentUserUid != null
                && loadedEvent != null
                && (currentUserUid.equals(loadedEvent.getOrganizerUid())
                || loadedEvent.isCoOrganizer(currentUserUid));
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        return HeliosText.trimToNull(value);
    }

    private String nonEmptyOr(String value, String fallback) {
        return HeliosText.nonEmptyOr(value, fallback);
    }

    private void toggleImageExpansion() {
        if (cardPoster == null || ivPoster == null) return;

        isImageExpanded = !isImageExpanded;

        TransitionManager.beginDelayedTransition((ViewGroup) requireView());

        ViewGroup.LayoutParams lp = cardPoster.getLayoutParams();
        if (isImageExpanded) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            ivPoster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ivPoster.setAdjustViewBounds(true);
            if (ivMaximizeIcon != null) {
                ivMaximizeIcon.setImageResource(R.drawable.ic_minimize);
            }
        } else {
            lp.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 188, getResources().getDisplayMetrics());
            ivPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivPoster.setAdjustViewBounds(false);
            if (ivMaximizeIcon != null) {
                ivMaximizeIcon.setImageResource(R.drawable.ic_maximize);
            }
        }
        cardPoster.setLayoutParams(lp);
    }
}
