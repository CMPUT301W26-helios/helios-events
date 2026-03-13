package com.example.helios.ui.nav;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.google.android.material.button.MaterialButton;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

public class OrganizerQrFragment extends Fragment {

    private enum Mode {
        CREATE,
        VIEW_EXISTING
    }

    private final EventService eventService = new EventService();
    private final ProfileService profileService = new ProfileService();

    private Mode mode = Mode.CREATE;

    @Nullable private String eventIdForView;

    private String title;
    private String description;
    private int maxEntrants;
    private boolean geoRequired;
    private long registrationOpensMillis;
    private long registrationClosesMillis;
    @Nullable private String posterUri;
    @Nullable private String tagsRaw;

    // Field so saveEvent() can update the QR image after save
    private ImageView qrImage;

    public OrganizerQrFragment() {
        super(R.layout.fragment_organizer_qr);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null && args.containsKey("arg_event_id")
                && !TextUtils.isEmpty(args.getString("arg_event_id"))) {
            mode = Mode.VIEW_EXISTING;
            eventIdForView = args.getString("arg_event_id");
            title = "";
            description = "";
            maxEntrants = 0;
            geoRequired = false;
            registrationOpensMillis = 0L;
            registrationClosesMillis = 0L;
            posterUri = null;
        } else if (args != null) {
            mode = Mode.CREATE;
            title = args.getString("arg_event_title", "");
            description = args.getString("arg_event_description", "");
            tagsRaw = args.getString("arg_event_tags", null);
            maxEntrants = args.getInt("arg_event_max_entrants", 0);
            geoRequired = args.getBoolean("arg_geolocation_required", false);
            registrationOpensMillis = args.getLong("arg_registration_opens_millis", 0L);
            registrationClosesMillis = args.getLong("arg_registration_closes_millis", 0L);
            posterUri = args.getString("arg_poster_uri", null);
        } else {
            mode = Mode.CREATE;
            title = "";
            description = "";
            maxEntrants = 0;
            geoRequired = false;
            registrationOpensMillis = 0L;
            registrationClosesMillis = 0L;
            posterUri = null;
            tagsRaw = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView headerTitle = view.findViewById(R.id.tv_qr_title);
        TextView labelView = view.findViewById(R.id.tv_generated_qr_label);
        qrImage = view.findViewById(R.id.image_qr_preview);
        View bottomActions = view.findViewById(R.id.layout_qr_bottom_actions);
        View previewEventButton = view.findViewById(R.id.button_preview_event_page);
        View inlineActions = view.findViewById(R.id.layout_qr_inline_actions);
        View cardView = view.findViewById(R.id.cv_qr_box);
        View innerLayout = view.findViewById(R.id.layout_qr_card_inner);

        MaterialButton cancelButton = view.findViewById(R.id.button_qr_cancel_back);
        MaterialButton confirmButton = view.findViewById(R.id.button_confirm_create_event);
        MaterialButton viewModeBackButton = view.findViewById(R.id.button_qr_back_view);

        if (mode == Mode.CREATE) {
            if (headerTitle != null) {
                headerTitle.setText("QR Code\nand Preview");
            }
            labelView.setText("Generated QR for: " + (title.isEmpty() ? "New Event" : title));

            // Show placeholder — real QR is generated after event is saved
            qrImage.setImageResource(android.R.drawable.ic_menu_gallery);

            if (bottomActions != null) bottomActions.setVisibility(View.VISIBLE);
            if (viewModeBackButton != null) viewModeBackButton.setVisibility(View.GONE);

            cancelButton.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigateUp());

            confirmButton.setOnClickListener(v -> saveEvent());

        } else {
            if (headerTitle != null) headerTitle.setText("QR Code");
            labelView.setText("Generated QR:");

            if (bottomActions != null) bottomActions.setVisibility(View.GONE);
            if (previewEventButton != null) previewEventButton.setVisibility(View.GONE);
            if (inlineActions != null) inlineActions.setVisibility(View.GONE);

            if (viewModeBackButton != null) {
                viewModeBackButton.setVisibility(View.VISIBLE);
                viewModeBackButton.setOnClickListener(v ->
                        NavHostFragment.findNavController(this).navigateUp());
            }

            if (innerLayout instanceof ConstraintLayout) {
                ConstraintSet innerSet = new ConstraintSet();
                innerSet.clone((ConstraintLayout) innerLayout);
                innerSet.connect(R.id.image_qr_preview, ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
                innerSet.applyTo((ConstraintLayout) innerLayout);
            }
            if (innerLayout != null) {
                ViewGroup.LayoutParams lp = innerLayout.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                innerLayout.setLayoutParams(lp);
            }
            if (cardView != null) {
                ViewGroup.LayoutParams cardLp = cardView.getLayoutParams();
                cardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                cardView.setLayoutParams(cardLp);
            }
            if (view instanceof ConstraintLayout) {
                ConstraintSet set = new ConstraintSet();
                set.clone((ConstraintLayout) view);
                set.clear(R.id.cv_qr_box, ConstraintSet.BOTTOM);
                set.applyTo((ConstraintLayout) view);
            }

            if (eventIdForView == null || eventIdForView.trim().isEmpty()) {
                Toast.makeText(requireContext(),
                        "Missing event id for QR view.", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }

            eventService.getEventById(eventIdForView, event -> {
                if (!isAdded() || event == null) return;
                String qrValue = event.getQrCodeValue();
                if (qrValue == null || qrValue.trim().isEmpty()) {
                    Toast.makeText(requireContext(),
                            "This event has no stored QR value.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap bmp = generateQrBitmap(qrValue, 512);
                if (bmp != null) qrImage.setImageBitmap(bmp);
            }, error -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to load event QR: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            });

            cancelButton.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigateUp());
            confirmButton.setVisibility(View.GONE);
        }
    }

    private void saveEvent() {
        if (title == null || title.trim().isEmpty()) {
            Toast.makeText(requireContext(),
                    "Event name missing. Go back and fill it in.", Toast.LENGTH_SHORT).show();
            return;
        }

        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();
            long now = System.currentTimeMillis();
            long oneWeek = 7L * 24 * 60 * 60 * 1000;

            java.util.List<String> interests = null;
            if (tagsRaw != null && !tagsRaw.trim().isEmpty()) {
                interests = new java.util.ArrayList<>();
                for (String part : tagsRaw.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) interests.add(trimmed);
                }
                if (interests.isEmpty()) interests = null;
            }

            Event event = new Event(
                    null, title, description, null, null,
                    now + oneWeek,
                    now + oneWeek + 3600000L,
                    registrationOpensMillis > 0 ? registrationOpensMillis : now,
                    registrationClosesMillis > 0 ? registrationClosesMillis : (now + (3L * 24 * 60 * 60 * 1000)),
                    maxEntrants > 0 ? maxEntrants : 0,
                    maxEntrants > 0 ? maxEntrants : 0,
                    null, geoRequired, "Lottery details TBD.",
                    uid, posterUri,
                    null, // qrCodeValue set after save
                    interests,
                    false
            );

            eventService.saveEvent(event, unused -> {
                // eventId is now assigned — set it as the QR value and save again
                event.setQrCodeValue(event.getEventId());
                eventService.saveEvent(event, unused2 -> {
                    if (!isAdded()) return;
                    // Generate and show the real QR with the eventId
                    Bitmap bmp = generateQrBitmap(event.getEventId(), 512);
                    if (bmp != null && qrImage != null) qrImage.setImageBitmap(bmp);

                    Toast.makeText(requireContext(),
                            "Event created: " + event.getTitle(), Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this)
                            .popBackStack(R.id.organizeFragment, false);
                }, error -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "Failed to update QR value: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }, error -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to create event: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            });

        }, error -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    "Auth failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    @Nullable
    private Bitmap generateQrBitmap(@NonNull String data, int sizePx) {
        if (data == null || data.trim().isEmpty()) return null;
        QRCodeWriter writer = new QRCodeWriter();
        try {
            com.google.zxing.common.BitMatrix bitMatrix =
                    writer.encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx);
            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return bmp;
        } catch (WriterException e) {
            if (isAdded()) {
                Toast.makeText(requireContext(),
                        "Failed to generate QR: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
            return null;
        }
    }
}