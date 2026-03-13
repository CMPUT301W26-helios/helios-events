package com.example.helios.ui.event;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.service.EntrantEventService;
import com.example.helios.service.EventService;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EventDetailsBottomSheet extends BottomSheetDialogFragment {

    public static final String ARG_EVENT_ID = "arg_event_id";
    public static final String ARG_HIDE_JOIN_BUTTON = "arg_hide_join_button";

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

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private ImageView ivPoster;
    private TextView tvName;
    private TextView tvDate;
    private TextView tvTimeStart;
    private TextView tvTimeEnd;
    private TextView tvLocation;
    private TextView tvDescription;
    private TextView tvCapacity;
    private MaterialButton btnWaitingList;

    private String eventId;
    private boolean hideJoinButton = false;
    private Event loadedEvent;
    private boolean isCurrentlyOnWaitingList = false;

    public EventDetailsBottomSheet() {
        super(R.layout.sheet_event_details);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() == null) return;
        View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
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

        View close = view.findViewById(R.id.button_close);
        if (close != null) {
            close.setOnClickListener(v -> dismiss());
        }

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
        if (tvName != null) {
            tvName.setText(nonEmptyOr(event.getTitle(), "Untitled Event"));
        }

        if (tvDate != null) {
            long start = event.getStartTimeMillis();
            tvDate.setText(start > 0 ? dateFormat.format(new Date(start)) : "Date TBD");
        }


        if (tvTimeStart != null) {
            long start = event.getStartTimeMillis();
            if (start > 0) {
                tvTimeStart.setText(timeFormat.format(new Date(start)));
            } else {
                tvTimeStart.setText("TBD");
            }
        }

        if (tvTimeEnd != null) {
            long end = event.getEndTimeMillis();
            if (end > 0) {
                tvTimeEnd.setText(timeFormat.format(new Date(end)));
            } else {
                tvTimeEnd.setText("TBD");
            }
        }

        if (tvLocation != null) {
            String loc = nonEmptyOr(event.getLocationName(), null);
            if (loc == null) loc = nonEmptyOr(event.getAddress(), "No location");
            tvLocation.setText(loc);
        }

        if (tvDescription != null) {
            tvDescription.setText(nonEmptyOr(event.getDescription(), ""));
        }

        if (ivPoster != null) {
            String posterImageId = event.getPosterImageId();
            if (posterImageId != null && !posterImageId.trim().isEmpty()) {
                try {
                    ivPoster.setImageURI(Uri.parse(posterImageId));
                    if (ivPoster.getDrawable() == null) {
                        ivPoster.setImageResource(R.drawable.elder_dance_poster_sample);
                    }
                } catch (Exception ignored) {
                    ivPoster.setImageResource(R.drawable.elder_dance_poster_sample);
                }
            } else {
                ivPoster.setImageResource(R.drawable.elder_dance_poster_sample);
            }
        }
    }

    private void refreshWaitingListState() {
        entrantEventService.getCurrentUserWaitingListEntry(requireContext(), eventId, entry -> {
            if (!isAdded()) return;

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

    private void refreshCapacityCount() {
        if (loadedEvent == null || tvCapacity == null) return;

        entrantEventService.getFilledSlotsCount(eventId, filled -> {
            if (!isAdded()) return;
            tvCapacity.setText("Waiting list capacity: " + filled + " / " + loadedEvent.getCapacity());
        }, error -> {
            if (!isAdded()) return;
            tvCapacity.setText("Waiting list capacity: ? / " + loadedEvent.getCapacity());
        });
    }
    private void showJoinWaitingListDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext());

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

    private void updateWaitingListButton() {
        if (btnWaitingList == null || hideJoinButton) return;

        btnWaitingList.setEnabled(true);
        if (isCurrentlyOnWaitingList) {
            btnWaitingList.setText("Leave Waiting List");
        } else {
            btnWaitingList.setText("Join Waiting List");
        }
    }

    private void setLoading(boolean loading) {
        if (tvName != null && loading) {
            tvName.setText("Loading...");
        }
        if (btnWaitingList != null && !hideJoinButton) {
            btnWaitingList.setEnabled(!loading);
        }
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private String nonEmptyOr(String value, String fallback) {
        if (value == null) return fallback;
        String t = value.trim();
        return t.isEmpty() ? fallback : t;
    }

    @Nullable
    private String getEventIdArg() {
        Bundle args = getArguments();
        if (args == null) return null;
        String id = args.getString(ARG_EVENT_ID);
        if (id == null) return null;
        id = id.trim();
        return id.isEmpty() ? null : id;
    }
}
