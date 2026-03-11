package com.example.helios.ui.nav;

import android.os.Bundle;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

/**
 * Organizer flow: step 2 of creating an event.
 * Shows QR placeholder and, on confirm, persists the event.
 */
public class CreateEventQrFragment extends Fragment {

    private final EventService eventService = new EventService();
    private final ProfileService profileService = new ProfileService();

    private String title;
    private String description;
    private int maxEntrants;
    private boolean geoRequired;
    private long registrationOpensMillis;
    private long registrationClosesMillis;
    @Nullable
    private String posterUri;

    public CreateEventQrFragment() {
        super(R.layout.fragment_create_event_qr);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            title = args.getString("arg_event_title", "");
            description = args.getString("arg_event_description", "");
            maxEntrants = args.getInt("arg_event_max_entrants", 0);
            geoRequired = args.getBoolean("arg_geolocation_required", false);
            registrationOpensMillis = args.getLong("arg_registration_opens_millis", 0L);
            registrationClosesMillis = args.getLong("arg_registration_closes_millis", 0L);
            posterUri = args.getString("arg_poster_uri", null);
        } else {
            title = "";
            description = "";
            maxEntrants = 0;
            geoRequired = false;
            registrationOpensMillis = 0L;
            registrationClosesMillis = 0L;
            posterUri = null;
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

        TextView titleView = view.findViewById(R.id.tv_generated_qr_label);
        titleView.setText("Generated QR for: " + (title.isEmpty() ? "New Event" : title));

        ImageView qrImage = view.findViewById(R.id.image_qr_preview);
        String qrPayload = buildQrPayload();
        Bitmap qrBitmap = generateQrBitmap(qrPayload, 512);
        if (qrBitmap != null) {
            qrImage.setImageBitmap(qrBitmap);
        }

        MaterialButton cancelButton = view.findViewById(R.id.button_qr_cancel_back);
        MaterialButton confirmButton = view.findViewById(R.id.button_confirm_create_event);

        cancelButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        confirmButton.setOnClickListener(v -> saveEvent());
    }

    private void saveEvent() {
        if (title == null || title.trim().isEmpty()) {
            Toast.makeText(requireContext(),
                    "Event name missing. Go back and fill it in.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        profileService.ensureSignedIn(firebaseUser -> {
            String uid = firebaseUser.getUid();

            long now = System.currentTimeMillis();
            long oneWeek = 7L * 24 * 60 * 60 * 1000;

            String qrPayload = buildQrPayload();

            Event event = new Event(
                    null,
                    title,
                    description,
                    null,
                    null,
                    now + oneWeek,          // startTimeMillis (placeholder)
                    now + oneWeek + 3600000L, // endTimeMillis (placeholder +1h)
                    registrationOpensMillis > 0 ? registrationOpensMillis : now,
                    registrationClosesMillis > 0 ? registrationClosesMillis : (now + (3L * 24 * 60 * 60 * 1000)),
                    maxEntrants > 0 ? maxEntrants : 0,
                    maxEntrants > 0 ? maxEntrants : 0,
                    null,
                    geoRequired,
                    "Lottery details TBD.",
                    uid,
                    posterUri,
                    qrPayload
            );

            eventService.saveEvent(
                    event,
                    unused -> {
                        Toast.makeText(requireContext(),
                                "Event created: " + event.getTitle(),
                                Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(this)
                                .popBackStack(R.id.organizeFragment, false);
                    },
                    error -> Toast.makeText(requireContext(),
                            "Failed to create event: " + error.getMessage(),
                            Toast.LENGTH_LONG).show()
            );

        }, error -> Toast.makeText(requireContext(),
                "Auth failed: " + error.getMessage(),
                Toast.LENGTH_LONG).show());
    }

    private String buildQrPayload() {
        String safeTitle = title == null ? "" : title.trim();
        String safeDescription = description == null ? "" : description.trim();
        return "helios:event:" + safeTitle + "|" + safeDescription;
    }

    @Nullable
    private Bitmap generateQrBitmap(@NonNull String data, int sizePx) {
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

