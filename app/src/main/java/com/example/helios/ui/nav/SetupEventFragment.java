package com.example.helios.ui.nav;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.service.EventService;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Shared fragment for creating a new event or editing an existing one.
 *
 * - If launched with an "arg_event_id", it loads and edits that event.
 * - If launched without "arg_event_id", it behaves like the old CreateEventFragment
 *   and forwards to the QR/preview step.
 */
public class SetupEventFragment extends Fragment {

    private enum Mode {
        CREATE,
        EDIT
    }

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    private final EventService eventService = new EventService();

    @Nullable
    private String eventId;
    @Nullable
    private Event loadedEvent;
    private Mode mode = Mode.CREATE;

    private long registrationOpensMillis = 0L;
    private long registrationClosesMillis = 0L;
    private boolean geolocationRequired = true;

    @Nullable
    private Uri selectedPosterUri = null;

    private final ActivityResultLauncher<String[]> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                persistReadPermission(uri);
                selectedPosterUri = uri;
                View v = getView();
                if (v == null) return;
                ImageView iv = v.findViewById(R.id.iv_upload_image);
                try {
                    iv.setImageURI(uri);
                } catch (SecurityException se) {
                    iv.setImageResource(android.R.drawable.ic_menu_upload);
                    Toast.makeText(requireContext(),
                            "Couldn't open that image. Please pick another one.",
                            Toast.LENGTH_SHORT).show();
                }
            });

    public SetupEventFragment() {
        super(R.layout.fragment_event_setup);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString("arg_event_id");
        }
        mode = (eventId == null || eventId.trim().isEmpty()) ? Mode.CREATE : Mode.EDIT;
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

        TextView titleView = view.findViewById(R.id.tv_create_event_title);
        EditText nameInput = view.findViewById(R.id.edit_event_name);
        EditText maxEntrantsInput = view.findViewById(R.id.edit_max_entrants);
        EditText descriptionInput = view.findViewById(R.id.edit_event_description);
        TextView startDateView = view.findViewById(R.id.tv_registration_start);
        TextView endDateView = view.findViewById(R.id.tv_registration_end);
        TextView geoOn = view.findViewById(R.id.tv_geo_on);
        TextView geoOff = view.findViewById(R.id.tv_geo_off);
        ImageView uploadImage = view.findViewById(R.id.iv_upload_image);

        MaterialButton cancelButton = view.findViewById(R.id.button_cancel_back);
        MaterialButton primaryButton = view.findViewById(R.id.button_next_qr);

        // Common controls
        cancelButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        uploadImage.setOnClickListener(v -> pickImageLauncher.launch(new String[]{"image/*"}));

        geoOn.setOnClickListener(v -> {
            geolocationRequired = true;
            updateGeoToggle(geoOn, geoOff, true);
        });

        geoOff.setOnClickListener(v -> {
            geolocationRequired = false;
            updateGeoToggle(geoOn, geoOff, false);
        });

        startDateView.setOnClickListener(v ->
                openDatePicker(registrationOpensMillis, picked -> {
                    registrationOpensMillis = picked;
                    startDateView.setText(dateFormat.format(new Date(picked)));
                })
        );

        endDateView.setOnClickListener(v ->
                openDatePicker(registrationClosesMillis, picked -> {
                    registrationClosesMillis = picked;
                    endDateView.setText(dateFormat.format(new Date(picked)));
                })
        );

        if (mode == Mode.CREATE) {
            // Initial UI text
            titleView.setText("Create Event");
            primaryButton.setText("Next - QR Code");

            // Defaults to match placeholders
            if (registrationOpensMillis == 0L || registrationClosesMillis == 0L) {
                Calendar c = Calendar.getInstance();
                c.set(2025, Calendar.MARCH, 1, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                registrationOpensMillis = c.getTimeInMillis();
                c.set(2025, Calendar.MARCH, 5, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                registrationClosesMillis = c.getTimeInMillis();
            }
            startDateView.setText(dateFormat.format(new Date(registrationOpensMillis)));
            endDateView.setText(dateFormat.format(new Date(registrationClosesMillis)));

            updateGeoToggle(geoOn, geoOff, geolocationRequired);

            primaryButton.setOnClickListener(v -> {
                String title = safeText(nameInput);
                String description = safeText(descriptionInput);
                String maxEntrantsStr = safeText(maxEntrantsInput);

                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(requireContext(),
                            "Event name is required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (registrationClosesMillis < registrationOpensMillis) {
                    Toast.makeText(requireContext(),
                            "Registration end date must be after start date.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                int maxEntrants = 0;
                if (!TextUtils.isEmpty(maxEntrantsStr)) {
                    try {
                        maxEntrants = Integer.parseInt(maxEntrantsStr);
                    } catch (NumberFormatException ignored) {
                    }
                }

                Bundle args = new Bundle();
                args.putString("arg_event_title", title);
                args.putString("arg_event_description", description);
                args.putInt("arg_event_max_entrants", maxEntrants);
                args.putLong("arg_registration_opens_millis", registrationOpensMillis);
                args.putLong("arg_registration_closes_millis", registrationClosesMillis);
                args.putBoolean("arg_geolocation_required", geolocationRequired);
                if (selectedPosterUri != null) {
                    args.putString("arg_poster_uri", selectedPosterUri.toString());
                }

                NavHostFragment.findNavController(this)
                        .navigate(R.id.createEventQrFragment, args);
            });
        } else {
            // EDIT mode
            titleView.setText("Edit Event");
            primaryButton.setText("Save");

            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(requireContext(),
                        "Missing event id.", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }

            eventService.getEventById(eventId, event -> {
                if (!isAdded() || event == null) return;
                loadedEvent = event;

                if (event.getTitle() != null) {
                    nameInput.setText(event.getTitle());
                }
                if (event.getDescription() != null) {
                    descriptionInput.setText(event.getDescription());
                }
                if (event.getCapacity() > 0) {
                    maxEntrantsInput.setText(String.valueOf(event.getCapacity()));
                }

                if (event.getRegistrationOpensMillis() > 0) {
                    registrationOpensMillis = event.getRegistrationOpensMillis();
                    startDateView.setText(dateFormat.format(
                            new Date(event.getRegistrationOpensMillis())));
                }
                if (event.getRegistrationClosesMillis() > 0) {
                    registrationClosesMillis = event.getRegistrationClosesMillis();
                    endDateView.setText(dateFormat.format(
                            new Date(event.getRegistrationClosesMillis())));
                }

                geolocationRequired = event.isGeolocationRequired();
                updateGeoToggle(geoOn, geoOff, geolocationRequired);

                if (!TextUtils.isEmpty(event.getPosterImageId())) {
                    Uri existingPosterUri = Uri.parse(event.getPosterImageId());
                    persistReadPermission(existingPosterUri);
                    try {
                        uploadImage.setImageURI(existingPosterUri);
                    } catch (SecurityException se) {
                        // Existing URI may have been saved from a one-time picker permission.
                        // Keep edit flow working and let organizer choose a new image if needed.
                        uploadImage.setImageResource(android.R.drawable.ic_menu_upload);
                    }
                }
            }, error -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Failed to load event: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            });

            primaryButton.setOnClickListener(v -> {
                if (loadedEvent == null) {
                    Toast.makeText(requireContext(),
                            "Event not loaded yet.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String title = safeText(nameInput);
                String description = safeText(descriptionInput);
                String maxEntrantsStr = safeText(maxEntrantsInput);

                if (TextUtils.isEmpty(title)) {
                    Toast.makeText(requireContext(),
                            "Event name is required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (registrationClosesMillis < registrationOpensMillis) {
                    Toast.makeText(requireContext(),
                            "Registration end date must be after start date.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                int maxEntrants = loadedEvent.getCapacity();
                if (!TextUtils.isEmpty(maxEntrantsStr)) {
                    try {
                        maxEntrants = Integer.parseInt(maxEntrantsStr);
                    } catch (NumberFormatException ignored) {
                    }
                }

                loadedEvent.setTitle(title);
                loadedEvent.setDescription(description);
                loadedEvent.setCapacity(maxEntrants);
                loadedEvent.setSampleSize(maxEntrants);
                loadedEvent.setRegistrationOpensMillis(registrationOpensMillis);
                loadedEvent.setRegistrationClosesMillis(registrationClosesMillis);
                loadedEvent.setGeolocationRequired(geolocationRequired);

                if (selectedPosterUri != null) {
                    loadedEvent.setPosterImageId(selectedPosterUri.toString());
                }

                eventService.saveEvent(
                        loadedEvent,
                        unused -> {
                            Toast.makeText(requireContext(),
                                    "Event updated.", Toast.LENGTH_SHORT).show();
                            NavHostFragment.findNavController(this)
                                    .popBackStack(R.id.manageEventFragment, false);
                        },
                        error -> Toast.makeText(requireContext(),
                                "Update failed: " + error.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            });
        }
    }

    private void persistReadPermission(@NonNull Uri uri) {
        if (getContext() == null) return;
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Some providers don't support persistable permissions; best effort is enough.
        }
    }

    private String safeText(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private interface DatePickedCallback {
        void onPicked(long millis);
    }

    private void openDatePicker(long initialMillis, @NonNull DatePickedCallback callback) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(initialMillis > 0 ? initialMillis : System.currentTimeMillis());

        new DatePickerDialog(
                requireContext(),
                (picker, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, 0, 0, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    callback.onPicked(picked.getTimeInMillis());
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void updateGeoToggle(@NonNull TextView on, @NonNull TextView off, boolean required) {
        if (required) {
            on.setBackgroundResource(R.drawable.bg_toggle_left_active);
            off.setBackgroundResource(R.drawable.bg_toggle_right_inactive);
        } else {
            on.setBackgroundResource(R.drawable.bg_toggle_left_inactive);
            off.setBackgroundResource(R.drawable.bg_toggle_right_active);
        }
    }
}
