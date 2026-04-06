package com.example.helios.ui.nav;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.example.helios.service.ProfileService;
import com.google.android.material.button.MaterialButton;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Fragment that displays the QR code for an event.
 * In CREATE mode, it generates the QR code after the event is saved.
 * In VIEW_EXISTING mode, it retrieves and displays the stored QR code value.
 */
public class OrganizerQrFragment extends Fragment {

    private enum Mode {
        CREATE,
        VIEW_EXISTING
    }

    private EventService eventService;
    private ProfileService profileService;

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
    private boolean isPrivateEvent = false;


    // Field so saveEvent() can update the QR image after save
    private ImageView qrImage;
    @Nullable private Bitmap currentQrBitmap;
    @Nullable private String currentQrValue;
    @Nullable private MaterialButton copyQrValueButton;
    @Nullable private MaterialButton saveQrImageButton;
    @Nullable private View qrUtilityActions;
    @Nullable private MaterialButton previewEventButton;

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) {
                    saveCurrentQrImage();
                } else {
                    Toast.makeText(requireContext(),
                            "Storage permission is required to save QR images on this Android version.",
                            Toast.LENGTH_SHORT).show();
                }
            });

    /**
     * Default constructor for OrganizerQrFragment.
     */
    public OrganizerQrFragment() {
        super(R.layout.fragment_organizer_qr);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        profileService = application.getProfileService();
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
            isPrivateEvent = args.getBoolean("arg_private_event", false);
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

        MaterialButton headerBackButton = view.findViewById(R.id.submenu_back_button);
        TextView headerTitle = view.findViewById(R.id.submenu_title);
        TextView headerSubtitle = view.findViewById(R.id.submenu_subtitle);
        TextView labelView = view.findViewById(R.id.tv_generated_qr_label);
        qrImage = view.findViewById(R.id.image_qr_preview);
        View bottomActions = view.findViewById(R.id.layout_qr_bottom_actions);
        previewEventButton = view.findViewById(R.id.button_preview_event_page);
        qrUtilityActions = view.findViewById(R.id.layout_qr_utility_actions);

        MaterialButton cancelButton = view.findViewById(R.id.button_qr_cancel_back);
        MaterialButton confirmButton = view.findViewById(R.id.button_confirm_create_event);
        MaterialButton viewModeBackButton = view.findViewById(R.id.button_qr_back_view);
        copyQrValueButton = view.findViewById(R.id.button_copy_qr_value);
        saveQrImageButton = view.findViewById(R.id.button_save_qr_image);
        headerBackButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        if (copyQrValueButton != null) {
            copyQrValueButton.setOnClickListener(v -> copyQrValueToClipboard());
            copyQrValueButton.setEnabled(false);
        }
        if (saveQrImageButton != null) {
            saveQrImageButton.setOnClickListener(v -> saveQrImageRequested());
            saveQrImageButton.setEnabled(false);
        }

        if (previewEventButton != null) {
            previewEventButton.setOnClickListener(v -> {
                if (currentQrValue != null) {
                    com.example.helios.ui.event.EventDetailsBottomSheet.newInstance(currentQrValue)
                            .show(getParentFragmentManager(), "event_details");
                }
            });
        }

        if (mode == Mode.CREATE) {
            if (headerTitle != null) {
                headerTitle.setText("Create Event QR");
            }
            if (headerSubtitle != null) {
                headerSubtitle.setText(title.isEmpty()
                        ? "Generate and confirm the promotional QR before finishing setup."
                        : "Generate and confirm the promotional QR for " + title + ".");
            }
            labelView.setText("Generated QR for: " + (title.isEmpty() ? "New Event" : title));

            // Show placeholder — real QR is generated after event is saved
            qrImage.setImageResource(android.R.drawable.ic_menu_gallery);

            if (bottomActions != null) bottomActions.setVisibility(View.VISIBLE);
            if (viewModeBackButton != null) viewModeBackButton.setVisibility(View.GONE);
            if (qrUtilityActions != null) qrUtilityActions.setVisibility(View.GONE);

            cancelButton.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigateUp());

            confirmButton.setOnClickListener(v -> saveEvent());

        } else {
            if (headerTitle != null) headerTitle.setText("Event QR Code");
            if (headerSubtitle != null) headerSubtitle.setText("Preview, copy, or save the event's promotional QR.");
            labelView.setText("Generated QR:");

            if (bottomActions != null) bottomActions.setVisibility(View.GONE);
            if (previewEventButton != null) previewEventButton.setVisibility(View.GONE);
            if (qrUtilityActions != null) qrUtilityActions.setVisibility(View.VISIBLE);

            if (viewModeBackButton != null) {
                viewModeBackButton.setVisibility(View.VISIBLE);
                viewModeBackButton.setOnClickListener(v ->
                        NavHostFragment.findNavController(this).navigateUp());
            }

            if (eventIdForView == null || eventIdForView.trim().isEmpty()) {
                Toast.makeText(requireContext(),
                        "Missing event id for QR view.", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }

            eventService.getEventById(eventIdForView, event -> {
                if (!isAdded() || event == null) return;
                if (event.isPrivateEvent()) {
                    Toast.makeText(requireContext(),
                            "Private events do not generate promotional QR codes.",
                            Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this).navigateUp();
                    return;
                }
                String qrValue = event.getQrCodeValue();
                if (qrValue == null || qrValue.trim().isEmpty()) {
                    Toast.makeText(requireContext(),
                            "This event has no stored QR value.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showQrValue(qrValue);
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

    /**
     * Saves the event to Firestore. Once the event is saved and an ID is assigned,
     * updates the event with its QR code value (the event ID) and generates the QR bitmap.
     */
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
                    0,
                    isPrivateEvent
            );

            eventService.saveEvent(event, unused -> {
                // eventId is now assigned — set it as the QR value and save again
                event.setQrCodeValue(event.getEventId());
                event.setPrivateEvent(isPrivateEvent);
                eventService.saveEvent(event, unused2 -> {
                    if (!isAdded()) return;
                    // Generate and show the real QR with the eventId
                    showQrValue(event.getEventId());

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

    private void showQrValue(@NonNull String qrValue) {
        Bitmap bmp = generateQrBitmap(qrValue, 512);
        currentQrValue = qrValue;
        currentQrBitmap = bmp;
        if (bmp != null && qrImage != null) {
            qrImage.setImageBitmap(bmp);
        }
        updateQrUtilityButtons(bmp != null && !qrValue.trim().isEmpty());
    }

    private void updateQrUtilityButtons(boolean enabled) {
        if (qrUtilityActions != null) {
            qrUtilityActions.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (previewEventButton != null) {
            previewEventButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (copyQrValueButton != null) {
            copyQrValueButton.setEnabled(enabled);
        }
        if (saveQrImageButton != null) {
            saveQrImageButton.setEnabled(enabled);
        }
    }

    private void copyQrValueToClipboard() {
        if (currentQrValue == null || currentQrValue.trim().isEmpty()) {
            Toast.makeText(requireContext(),
                    "No QR code value to copy yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboardManager =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            Toast.makeText(requireContext(),
                    "Clipboard service unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboardManager.setPrimaryClip(
                ClipData.newPlainText("Helios QR Code", currentQrValue)
        );
        Toast.makeText(requireContext(),
                "QR code copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    private void saveQrImageRequested() {
        if (currentQrBitmap == null) {
            Toast.makeText(requireContext(),
                    "No QR image to save yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        saveCurrentQrImage();
    }

    private void saveCurrentQrImage() {
        if (currentQrBitmap == null) {
            Toast.makeText(requireContext(),
                    "No QR image to save yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        String displayName = buildQrImageFileName();
        try {
            Uri savedUri = saveBitmapToMediaStore(currentQrBitmap, displayName);
            if (savedUri == null) {
                Toast.makeText(requireContext(),
                        "Failed to save QR image.", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(),
                    "QR image saved to Pictures/Helios.",
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(),
                    "Failed to save QR image: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private Uri saveBitmapToMediaStore(@NonNull Bitmap bitmap, @NonNull String displayName)
            throws IOException {
        ContentResolver resolver = requireContext().getContentResolver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Helios");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;

            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                if (outputStream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    resolver.delete(uri, null, null);
                    return null;
                }
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }

            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
        }

        String url = MediaStore.Images.Media.insertImage(
                resolver,
                bitmap,
                displayName,
                "Helios event QR code"
        );
        return TextUtils.isEmpty(url) ? null : Uri.parse(url);
    }

    @NonNull
    private String buildQrImageFileName() {
        String baseName = !TextUtils.isEmpty(title)
                ? title
                : (!TextUtils.isEmpty(eventIdForView) ? eventIdForView : "event");
        String normalized = baseName.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            normalized = "event";
        }
        return "helios_qr_" + normalized + ".png";
    }

    /**
     * Generates a QR code bitmap for the given string data using the ZXing library.
     *
     * @param data   The string to encode in the QR code.
     * @param sizePx The size of the bitmap in pixels.
     * @return The generated QR code bitmap, or null if generation fails.
     */
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
