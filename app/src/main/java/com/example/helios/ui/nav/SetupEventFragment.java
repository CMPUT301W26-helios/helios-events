package com.example.helios.ui.nav;

import android.app.DatePickerDialog;
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

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                selectedPosterUri = uri;
                View v = getView();
                if (v == null) return;
                ImageView iv = v.findViewById(R.id.iv_upload_image);
                iv.setImageURI(uri);
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
        EditText tagsInput = view.findViewById(R.id.edit_event_tags);
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

        uploadImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

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
                String tagsRaw = safeText(tagsInput);
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
                args.putString("arg_event_tags", tagsRaw);
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
                if (event.getInterests() != null && !event.getInterests().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < event.getInterests().size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(event.getInterests().get(i));
                    }
                    tagsInput.setText(sb.toString());
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

                // We keep existing posterImageId in the Event model.
                // If the user picks a new image, we override it when saving.
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
                String tagsRaw = safeText(tagsInput);
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

                // Update interests from comma-separated tags.
                java.util.List<String> interests = null;
                if (!tagsRaw.isEmpty()) {
                    interests = new java.util.ArrayList<>();
                    for (String part : tagsRaw.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            interests.add(trimmed);
                        }
                    }
                    if (interests.isEmpty()) {
                        interests = null;
                    }
                }
                loadedEvent.setInterests(interests);

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

