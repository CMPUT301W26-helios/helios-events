package com.example.helios.ui.nav;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.ImageAsset;
import com.example.helios.service.EventService;
import com.example.helios.service.ImageService;
import com.example.helios.service.ProfileService;
import com.example.helios.ui.common.EventNavArgs;
import com.example.helios.ui.common.HeliosImageUploader;
import com.example.helios.ui.common.HeliosLocation;
import com.example.helios.ui.common.HeliosUi;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SetupEventFragment extends Fragment {

    private enum Mode { CREATE, EDIT }

    private static final class PosterUploadCleanupState {
        @Nullable private String storagePath;
        @Nullable private String imageAssetId;
    }

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private EventService eventService;
    private ImageService imageService;
    private ProfileService profileService;
    private boolean isPrivateEvent = false;

    @Nullable private String eventId;
    @Nullable private Event loadedEvent;
    private Mode mode = Mode.CREATE;

    private long registrationOpensMillis = 0L;
    private long registrationClosesMillis = 0L;
    private long eventStartMillis = 0L;
    private long eventEndMillis = 0L;
    private boolean geolocationRequired = true;
    @Nullable private FusedLocationProviderClient locationClient;
    @Nullable private Runnable pendingLocationAction;

    @Nullable private Uri pendingPosterUri = null;
    @Nullable private ImageView uploadImageView;
    @Nullable private ProgressBar uploadProgressBar;
    @Nullable private MaterialButton primaryButton;
    @Nullable private MaterialButton cancelButton;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                persistReadPermission(uri);
                pendingPosterUri = uri;
                showPosterPreview(uri);
            });

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handleLocationPermissionResult);
    private final ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> handleLocationSettingsResolutionResult(result.getResultCode() == Activity.RESULT_OK));

    public SetupEventFragment() {
        super(R.layout.fragment_event_setup);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HeliosApplication application = HeliosApplication.from(requireContext());
        eventService = application.getEventService();
        imageService = application.getImageService();
        profileService = application.getProfileService();
        Bundle args = getArguments();
        eventId = EventNavArgs.getEventId(args);
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

        MaterialButton headerBackButton = view.findViewById(R.id.submenu_back_button);
        TextView titleView = view.findViewById(R.id.submenu_title);
        TextView subtitleView = view.findViewById(R.id.submenu_subtitle);
        EditText nameInput = view.findViewById(R.id.edit_event_name);
        EditText locationNameInput = view.findViewById(R.id.edit_event_location_name);
        EditText addressInput = view.findViewById(R.id.edit_event_address);
        EditText maxEntrantsInput = view.findViewById(R.id.edit_max_entrants);
        EditText waitlistLimitInput = view.findViewById(R.id.edit_waitlist_limit);
        EditText descriptionInput = view.findViewById(R.id.edit_event_description);
        EditText lotteryGuidelinesInput = view.findViewById(R.id.edit_lottery_guidelines);
        EditText tagsInput = view.findViewById(R.id.edit_event_tags);
        EditText geofenceLatitudeInput = view.findViewById(R.id.edit_geofence_latitude);
        EditText geofenceLongitudeInput = view.findViewById(R.id.edit_geofence_longitude);
        EditText geofenceRadiusInput = view.findViewById(R.id.edit_geofence_radius);
        TextView registrationStartDateView = view.findViewById(R.id.tv_registration_start);
        TextView registrationEndDateView = view.findViewById(R.id.tv_registration_end);
        TextView registrationEndTimeView = view.findViewById(R.id.tv_registration_end_time);
        TextView eventDateView = view.findViewById(R.id.tv_event_start_date);
        TextView eventStartTimeView = view.findViewById(R.id.tv_event_start_time);
        TextView eventEndTimeView = view.findViewById(R.id.tv_event_end_time);
        TextView geoOn = view.findViewById(R.id.tv_geo_on);
        TextView geoOff = view.findViewById(R.id.tv_geo_off);
        TextView privateOn = view.findViewById(R.id.tv_private_on);
        TextView privateOff = view.findViewById(R.id.tv_private_off);
        LinearLayout geofenceFields = view.findViewById(R.id.layout_geofence_fields);
        LinearLayout manualCoordinatesFields = view.findViewById(R.id.layout_manual_coordinates_fields);
        MaterialButton useCurrentLocationButton = view.findViewById(R.id.button_use_current_location);
        MaterialButton manualCoordinatesToggle = view.findViewById(R.id.button_toggle_manual_coordinates);
        MaterialButton previewGeofenceButton = view.findViewById(R.id.button_preview_geofence);
        uploadImageView = view.findViewById(R.id.iv_upload_image);
        uploadProgressBar = view.findViewById(R.id.progress_poster_upload);

        cancelButton = view.findViewById(R.id.button_cancel_back);
        primaryButton = view.findViewById(R.id.button_next_qr);

        headerBackButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        cancelButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        uploadImageView.setOnClickListener(v -> launchImagePicker());
        useCurrentLocationButton.setOnClickListener(v ->
                requestCurrentLocationForGeofence(
                        geofenceLatitudeInput,
                        geofenceLongitudeInput,
                        locationNameInput
                ));
        manualCoordinatesToggle.setOnClickListener(v ->
                updateManualCoordinatesVisibility(
                        manualCoordinatesFields,
                        manualCoordinatesToggle,
                        manualCoordinatesFields.getVisibility() != View.VISIBLE
                ));

        if (previewGeofenceButton != null) {
            previewGeofenceButton.setOnClickListener(v ->
                    openGeofencePreview(geofenceLatitudeInput, geofenceLongitudeInput));
            android.text.TextWatcher coordWatcher = new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    updatePreviewButtonVisibility(previewGeofenceButton,
                            geofenceLatitudeInput, geofenceLongitudeInput);
                }
            };
            geofenceLatitudeInput.addTextChangedListener(coordWatcher);
            geofenceLongitudeInput.addTextChangedListener(coordWatcher);
        }

        geoOn.setOnClickListener(v -> {
            geolocationRequired = true;
            updateGeoToggle(geoOn, geoOff, true);
            updateGeofenceSectionVisibility(geofenceFields, true);
        });
        geoOff.setOnClickListener(v -> {
            geolocationRequired = false;
            updateGeoToggle(geoOn, geoOff, false);
            updateGeofenceSectionVisibility(geofenceFields, false);
        });

        privateOn.setOnClickListener(v -> {
            isPrivateEvent = true;
            updatePrivateToggle(privateOn, privateOff, true);
            if (mode == Mode.CREATE) updateCreatePrimaryButtonLabel(primaryButton);
        });
        privateOff.setOnClickListener(v -> {
            isPrivateEvent = false;
            updatePrivateToggle(privateOn, privateOff, false);
            if (mode == Mode.CREATE) updateCreatePrimaryButtonLabel(primaryButton);
        });
        updatePrivateToggle(privateOn, privateOff, isPrivateEvent);

        registrationStartDateView.setOnClickListener(v ->
                openDatePicker(registrationOpensMillis, picked -> {
                    registrationOpensMillis = updateDatePortion(
                            registrationOpensMillis,
                            picked,
                            0,
                            0
                    );
                    updateRegistrationWindowViews(
                            registrationStartDateView,
                            registrationEndDateView,
                            registrationEndTimeView
                    );
                }));

        registrationEndDateView.setOnClickListener(v ->
                openDatePicker(registrationClosesMillis, picked -> {
                    registrationClosesMillis = updateDatePortion(
                            registrationClosesMillis,
                            picked,
                            23,
                            59
                    );
                    updateRegistrationWindowViews(
                            registrationStartDateView,
                            registrationEndDateView,
                            registrationEndTimeView
                    );
                }));

        registrationEndTimeView.setOnClickListener(v ->
                openTimePicker(registrationClosesMillis, (hourOfDay, minute) -> {
                    registrationClosesMillis = updateTimePortion(
                            registrationClosesMillis,
                            hourOfDay,
                            minute,
                            registrationOpensMillis > 0
                                    ? registrationOpensMillis
                                    : System.currentTimeMillis()
                    );
                    updateRegistrationWindowViews(
                            registrationStartDateView,
                            registrationEndDateView,
                            registrationEndTimeView
                    );
                }));

        eventDateView.setOnClickListener(v ->
                openDatePicker(eventStartMillis, picked -> {
                    long baseDate = picked > 0 ? picked : System.currentTimeMillis();
                    eventStartMillis = updateDatePortion(eventStartMillis, baseDate, 18, 0);
                    eventEndMillis = updateDatePortion(eventEndMillis, baseDate, 19, 0);
                    updateEventWindowViews(eventDateView, eventStartTimeView, eventEndTimeView);
                }));

        eventStartTimeView.setOnClickListener(v ->
                openTimePicker(eventStartMillis, (hourOfDay, minute) -> {
                    long dateBase = eventStartMillis > 0
                            ? eventStartMillis
                            : (eventEndMillis > 0 ? eventEndMillis : System.currentTimeMillis());
                    eventStartMillis = updateTimePortion(eventStartMillis, hourOfDay, minute, dateBase);
                    updateEventWindowViews(eventDateView, eventStartTimeView, eventEndTimeView);
                }));

        eventEndTimeView.setOnClickListener(v ->
                openTimePicker(eventEndMillis, (hourOfDay, minute) -> {
                    long dateBase = eventEndMillis > 0
                            ? eventEndMillis
                            : (eventStartMillis > 0 ? eventStartMillis : System.currentTimeMillis());
                    eventEndMillis = updateTimePortion(eventEndMillis, hourOfDay, minute, dateBase);
                    updateEventWindowViews(eventDateView, eventStartTimeView, eventEndTimeView);
                }));

        subtitleView.setVisibility(View.GONE);
        updateManualCoordinatesVisibility(manualCoordinatesFields, manualCoordinatesToggle, false);

        if (mode == Mode.CREATE) {
            titleView.setText("Create Event");
            updateCreatePrimaryButtonLabel(primaryButton);

            if (registrationOpensMillis == 0L || registrationClosesMillis == 0L) {
                initializeDefaultRegistrationWindow();
            }
            if (eventStartMillis == 0L || eventEndMillis == 0L) {
                initializeDefaultEventWindow();
            }
            updateRegistrationWindowViews(
                    registrationStartDateView,
                    registrationEndDateView,
                    registrationEndTimeView
            );
            updateEventWindowViews(eventDateView, eventStartTimeView, eventEndTimeView);
            updateGeoToggle(geoOn, geoOff, geolocationRequired);
            updateGeofenceSectionVisibility(geofenceFields, geolocationRequired);

            primaryButton.setOnClickListener(v -> {
                SetupEventFormData formData = SetupEventFormData.from(
                        nameInput,
                        descriptionInput,
                        lotteryGuidelinesInput,
                        locationNameInput,
                        addressInput,
                        tagsInput,
                        geofenceLatitudeInput,
                        geofenceLongitudeInput,
                        geofenceRadiusInput,
                        maxEntrantsInput,
                        waitlistLimitInput
                );
                String validationError = formData.validate(
                        registrationOpensMillis,
                        registrationClosesMillis,
                        eventStartMillis,
                        eventEndMillis,
                        geolocationRequired
                );
                if (validationError != null) {
                    HeliosUi.toast(this, validationError);
                    return;
                }
                profileService.loadCurrentProfile(requireContext(), profile -> {
                    if (!isAdded()) return;
                    if (profile != null && profile.isOrganizerAccessRevoked()) {
                        HeliosUi.toast(this,
                                "Organizer access is restricted for this profile.");
                        return;
                    }
                    createEvent(formData, formData.resolveCapacity(0));
                }, error -> {
                    if (!isAdded()) return;
                    HeliosUi.toast(this,
                            "Failed to load organizer profile: " + error.getMessage());
                });
            });

        } else {
            titleView.setText("Edit Event");
            primaryButton.setText("Save");

            if (eventId == null || eventId.trim().isEmpty()) {
                HeliosUi.toast(this, "Missing event id.");
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }

            eventService.getEventById(eventId, event -> {
                if (!isAdded() || event == null) return;
                loadedEvent = event;

                if (event.getTitle() != null) nameInput.setText(event.getTitle());
                if (!TextUtils.isEmpty(event.getLocationName())) {
                    locationNameInput.setText(event.getLocationName());
                }
                if (!TextUtils.isEmpty(event.getAddress())) {
                    addressInput.setText(event.getAddress());
                }
                if (event.getDescription() != null) descriptionInput.setText(event.getDescription());
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
                if (event.getWaitlistLimit() != null && event.getWaitlistLimit() > 0) {
                    waitlistLimitInput.setText(String.valueOf(event.getWaitlistLimit()));
                }
                if (!TextUtils.isEmpty(event.getLotteryGuidelines())) {
                    lotteryGuidelinesInput.setText(event.getLotteryGuidelines());
                }
                if (event.getRegistrationOpensMillis() > 0) {
                    registrationOpensMillis = event.getRegistrationOpensMillis();
                }
                if (event.getRegistrationClosesMillis() > 0) {
                    registrationClosesMillis = event.getRegistrationClosesMillis();
                }
                if (event.getStartTimeMillis() > 0) {
                    eventStartMillis = event.getStartTimeMillis();
                }
                if (event.getEndTimeMillis() > 0) {
                    eventEndMillis = event.getEndTimeMillis();
                }
                updateRegistrationWindowViews(
                        registrationStartDateView,
                        registrationEndDateView,
                        registrationEndTimeView
                );
                updateEventWindowViews(eventDateView, eventStartTimeView, eventEndTimeView);

                geolocationRequired = event.isGeolocationRequired();
                updateGeoToggle(geoOn, geoOff, geolocationRequired);
                updateGeofenceSectionVisibility(geofenceFields, geolocationRequired);
                bindExistingGeofence(event, geofenceLatitudeInput, geofenceLongitudeInput, geofenceRadiusInput);

                isPrivateEvent = event.isPrivateEvent();
                updatePrivateToggle(privateOn, privateOff, isPrivateEvent);

                if (!TextUtils.isEmpty(event.getPosterImageId())) {
                    showPosterPreview(event.getPosterImageId());
                } else {
                    showPosterPlaceholder();
                }
            }, error -> {
                if (!isAdded()) return;
                HeliosUi.toastLong(this, "Failed to load event: " + error.getMessage());
            });

            primaryButton.setOnClickListener(v -> {
                if (loadedEvent == null) {
                    HeliosUi.toast(this, "Event not loaded yet.");
                    return;
                }

                SetupEventFormData formData = SetupEventFormData.from(
                        nameInput,
                        descriptionInput,
                        lotteryGuidelinesInput,
                        locationNameInput,
                        addressInput,
                        tagsInput,
                        geofenceLatitudeInput,
                        geofenceLongitudeInput,
                        geofenceRadiusInput,
                        maxEntrantsInput,
                        waitlistLimitInput
                );
                String validationError = formData.validate(
                        registrationOpensMillis,
                        registrationClosesMillis,
                        eventStartMillis,
                        eventEndMillis,
                        geolocationRequired
                );
                if (validationError != null) {
                    HeliosUi.toast(this, validationError);
                    return;
                }

                formData.applyTo(
                        loadedEvent,
                        formData.resolveCapacity(loadedEvent.getCapacity()),
                        registrationOpensMillis,
                        registrationClosesMillis,
                        eventStartMillis,
                        eventEndMillis,
                        geolocationRequired,
                        isPrivateEvent
                );

                if (pendingPosterUri != null) {
                    setUploadInProgress(true);
                    uploadPosterAndSaveEvent(
                            loadedEvent,
                            pendingPosterUri,
                            unused -> {
                                if (!isAdded()) return;
                                setUploadInProgress(false);
                                HeliosUi.toast(this, "Event updated.");
                                NavHostFragment.findNavController(this)
                                        .popBackStack(R.id.manageEventFragment, false);
                            },
                            error -> {
                                if (!isAdded()) return;
                                setUploadInProgress(false);
                                HeliosUi.toastLong(this, "Update failed: "
                                        + HeliosImageUploader.getUserFacingUploadErrorMessage(error));
                            }
                    );
                    return;
                }

                eventService.saveEvent(
                        loadedEvent,
                        unused -> {
                            HeliosUi.toast(this, "Event updated.");
                            NavHostFragment.findNavController(this)
                                    .popBackStack(R.id.manageEventFragment, false);
                        },
                        error -> HeliosUi.toastLong(this, "Update failed: " + error.getMessage())
                );
            });
        }
    }

    private void createEvent(@NonNull SetupEventFormData formData, int maxEntrants) {
        profileService.ensureSignedIn(
                firebaseUser -> {
                    Event event = buildCreateModeEvent(
                            formData,
                            maxEntrants,
                            isPrivateEvent,
                            firebaseUser.getUid()
                    );
                    setUploadInProgress(true);
                    eventService.saveEvent(event, unused -> {
                        if (event.isPrivateEvent()) {
                            event.setQrCodeValue(null);
                        } else {
                            event.setQrCodeValue(event.getEventId());
                        }

                        OnSuccessListener<Void> finalizeSuccess = ignored -> {
                            if (!isAdded()) return;
                            setUploadInProgress(false);
                            String createdEventId = event.getEventId();
                            if (createdEventId == null || createdEventId.trim().isEmpty()) {
                                HeliosUi.toastLong(this, "Event created but could not be opened.");
                                NavHostFragment.findNavController(this)
                                        .popBackStack(R.id.organizeFragment, false);
                                return;
                            }

                            HeliosUi.toast(this, event.isPrivateEvent() ? "Private event created." : "Event created.");

                            NavOptions navOptions = new NavOptions.Builder()
                                    .setPopUpTo(R.id.createEventFragment, true)
                                    .build();
                            NavHostFragment.findNavController(this)
                                    .navigate(
                                            R.id.manageEventFragment,
                                            EventNavArgs.forEventIdAndOpenPosting(createdEventId),
                                            navOptions
                                    );
                        };

                        OnFailureListener finalizeFailure = error -> {
                            rollbackCreatedEvent(event, null, error);
                        };

                        if (pendingPosterUri != null) {
                            PosterUploadCleanupState cleanupState = new PosterUploadCleanupState();
                            uploadPosterAndSaveEvent(
                                    event,
                                    pendingPosterUri,
                                    finalizeSuccess,
                                    error -> rollbackCreatedEvent(event, cleanupState, error),
                                    cleanupState
                            );
                        } else {
                            eventService.saveEvent(event, finalizeSuccess, finalizeFailure);
                        }
                    }, error -> {
                        if (!isAdded()) return;
                        setUploadInProgress(false);
                        HeliosUi.toastLong(this, "Create failed: " + error.getMessage());
                    });
                },
                error -> {
                    if (!isAdded()) return;
                    HeliosUi.toastLong(this, "Auth failed: " + error.getMessage());
                }
        );
    }

    @NonNull
    private Event buildCreateModeEvent(
            @NonNull SetupEventFormData formData,
            int maxEntrants,
            boolean privateEvent,
            @NonNull String organizerUid
    ) {
        long now = System.currentTimeMillis();
        long oneWeek = 7L * 24 * 60 * 60 * 1000;
        long resolvedEventStart = eventStartMillis > 0 ? eventStartMillis : (now + oneWeek);
        long resolvedEventEnd = eventEndMillis > 0 ? eventEndMillis : (resolvedEventStart + 3600000L);

        Event event = new Event(
                null,
                formData.getTitle(),
                formData.getDescription(),
                formData.getLocationName(),
                formData.getAddress(),
                resolvedEventStart,
                resolvedEventEnd,
                registrationOpensMillis > 0 ? registrationOpensMillis : now,
                registrationClosesMillis > 0 ? registrationClosesMillis : (now + (3L * 24 * 60 * 60 * 1000)),
                maxEntrants > 0 ? maxEntrants : 0,
                maxEntrants > 0 ? maxEntrants : 0,
                formData.resolveWaitlistLimit(),
                geolocationRequired,
                formData.getLotteryGuidelines(),
                organizerUid,
                null,
                null,
                formData.getInterests(),
                0,
                privateEvent
        );
        Double geofenceLatitude = formData.resolveGeofenceLatitude();
        Double geofenceLongitude = formData.resolveGeofenceLongitude();
        Integer geofenceRadiusMeters = formData.resolveGeofenceRadiusMeters();
        if (geofenceLatitude != null && geofenceLongitude != null && geofenceRadiusMeters != null) {
            event.setGeofenceCenter(geofenceLatitude, geofenceLongitude);
            event.setGeofenceRadiusMeters(geofenceRadiusMeters);
        }
        return event;
    }

    private void uploadPosterAndSaveEvent(
            @NonNull Event event,
            @NonNull Uri posterUri,
            @NonNull OnSuccessListener<Void> onDone,
            @NonNull OnFailureListener onFailure
    ) {
        uploadPosterAndSaveEvent(event, posterUri, onDone, onFailure, null);
    }

    private void uploadPosterAndSaveEvent(
            @NonNull Event event,
            @NonNull Uri posterUri,
            @NonNull OnSuccessListener<Void> onDone,
            @NonNull OnFailureListener onFailure,
            @Nullable PosterUploadCleanupState cleanupState
    ) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalStateException("Event must be saved before uploading a poster."));
            return;
        }

        replaceExistingPosterIfNeeded(event, () -> {
            HeliosImageUploader.uploadImage(
                    requireContext(),
                    posterUri,
                    "event_posters/" + eventId + "/" + UUID.randomUUID(),
                    uploadResult -> {
                        if (cleanupState != null) {
                            cleanupState.storagePath = uploadResult.getStoragePath();
                        }
                        event.setPosterImageId(uploadResult.getDownloadUrl());

                        ImageAsset imageAsset = new ImageAsset();
                        imageAsset.setOwnerUid(event.getOrganizerUid());
                        imageAsset.setEventId(eventId);
                        imageAsset.setStoragePath(uploadResult.getStoragePath());
                        imageAsset.setUploadedAtMillis(System.currentTimeMillis());

                        imageService.saveImageAsset(
                                imageAsset,
                                unused -> {
                                    if (cleanupState != null) {
                                        cleanupState.imageAssetId = imageAsset.getImageId();
                                    }
                                    eventService.saveEvent(event, onDone, onFailure);
                                },
                                onFailure
                        );
                    },
                    onFailure
            );
        }, onFailure);
    }

    private void rollbackCreatedEvent(
            @NonNull Event event,
            @Nullable PosterUploadCleanupState cleanupState,
            @NonNull Exception cause
    ) {
        String createdEventId = event.getEventId();
        if (createdEventId == null || createdEventId.trim().isEmpty()) {
            showCreateFailureMessage(cause, null, null);
            return;
        }

        cleanupPosterArtifacts(
                cleanupState,
                null,
                cleanupError -> deleteCreatedEvent(createdEventId, cause, cleanupError),
                () -> deleteCreatedEvent(createdEventId, cause, null)
        );
    }

    private void deleteCreatedEvent(
            @NonNull String eventId,
            @NonNull Exception cause,
            @Nullable Exception cleanupError
    ) {
        eventService.deleteEvent(
                eventId,
                unused -> showCreateFailureMessage(cause, cleanupError, null),
                deleteError -> showCreateFailureMessage(cause, cleanupError, deleteError)
        );
    }

    private void cleanupPosterArtifacts(
            @Nullable PosterUploadCleanupState cleanupState,
            @Nullable Exception cleanupError,
            @NonNull OnFailureListener onCleanupFailure,
            @NonNull Runnable onCleanupComplete
    ) {
        if (cleanupState == null) {
            onCleanupComplete.run();
            return;
        }

        String storagePath = cleanupState.storagePath;
        if (storagePath == null || storagePath.trim().isEmpty()) {
            deleteUploadedImageAssetIfNeeded(cleanupState, cleanupError, onCleanupFailure, onCleanupComplete);
            return;
        }

        HeliosImageUploader.deleteFromStorage(
                requireContext(),
                storagePath,
                () -> deleteUploadedImageAssetIfNeeded(cleanupState, cleanupError, onCleanupFailure, onCleanupComplete),
                error -> deleteUploadedImageAssetIfNeeded(cleanupState, error, onCleanupFailure, onCleanupComplete)
        );
    }

    private void deleteUploadedImageAssetIfNeeded(
            @NonNull PosterUploadCleanupState cleanupState,
            @Nullable Exception cleanupError,
            @NonNull OnFailureListener onCleanupFailure,
            @NonNull Runnable onCleanupComplete
    ) {
        String imageAssetId = cleanupState.imageAssetId;
        if (imageAssetId == null || imageAssetId.trim().isEmpty()) {
            if (cleanupError != null) {
                onCleanupFailure.onFailure(cleanupError);
                return;
            }
            onCleanupComplete.run();
            return;
        }

        imageService.deleteImageAsset(
                imageAssetId,
                unused -> {
                    if (cleanupError != null) {
                        onCleanupFailure.onFailure(cleanupError);
                        return;
                    }
                    onCleanupComplete.run();
                },
                error -> {
                    if (cleanupError != null) {
                        onCleanupFailure.onFailure(cleanupError);
                        return;
                    }
                    onCleanupFailure.onFailure(error);
                }
        );
    }

    private void showCreateFailureMessage(
            @NonNull Exception cause,
            @Nullable Exception cleanupError,
            @Nullable Exception deleteError
    ) {
        if (!isAdded()) return;
        setUploadInProgress(false);

        String failureMessage = HeliosImageUploader.getUserFacingUploadErrorMessage(cause);

        String message = "Create failed: " + failureMessage;
        if (deleteError != null) {
            String deleteMessage = deleteError.getMessage();
            if (deleteMessage == null || deleteMessage.trim().isEmpty()) {
                deleteMessage = "cleanup was incomplete.";
            }
            message = "Create failed and the draft event could not be removed: " + deleteMessage;
        } else if (cleanupError != null) {
            String cleanupMessage = cleanupError.getMessage();
            if (cleanupMessage == null || cleanupMessage.trim().isEmpty()) {
                cleanupMessage = "poster cleanup was incomplete.";
            }
            message = "Create failed. The draft event was removed, but poster cleanup may be incomplete: "
                    + cleanupMessage;
        }

        HeliosUi.toastLong(this, message);
    }

    private void replaceExistingPosterIfNeeded(
            @NonNull Event event,
            @NonNull Runnable onReady,
            @NonNull OnFailureListener onFailure
    ) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            onReady.run();
            return;
        }

        imageService.getImageAssetForEvent(eventId, existingAsset -> {
            if (existingAsset == null
                    || existingAsset.getStoragePath() == null
                    || existingAsset.getStoragePath().trim().isEmpty()) {
                onReady.run();
                return;
            }

            HeliosImageUploader.deleteFromStorage(
                    requireContext(),
                    existingAsset.getStoragePath(),
                    () -> deleteExistingImageAsset(existingAsset, onReady, onFailure),
                    onFailure
            );
        }, onFailure);
    }

    private void deleteExistingImageAsset(
            @NonNull ImageAsset existingAsset,
            @NonNull Runnable onReady,
            @NonNull OnFailureListener onFailure
    ) {
        String imageId = existingAsset.getImageId();
        if (imageId == null || imageId.trim().isEmpty()) {
            onReady.run();
            return;
        }

        imageService.deleteImageAsset(imageId, unused -> onReady.run(), onFailure);
    }

    private void showPosterPreview(@NonNull Uri uri) {
        if (uploadImageView == null || !isAdded()) return;
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.placeholder_event)
                .error(R.drawable.placeholder_event)
                .into(uploadImageView);
    }

    private void showPosterPreview(@NonNull String imageUrl) {
        if (uploadImageView == null || !isAdded()) return;
        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_event)
                .error(R.drawable.placeholder_event)
                .into(uploadImageView);
    }

    private void showPosterPlaceholder() {
        if (uploadImageView == null) return;
        uploadImageView.setImageResource(R.drawable.placeholder_event);
    }

    private void setUploadInProgress(boolean inProgress) {
        if (uploadProgressBar != null) {
            uploadProgressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        }
        if (primaryButton != null) {
            primaryButton.setEnabled(!inProgress);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(!inProgress);
        }
        if (uploadImageView != null) {
            uploadImageView.setEnabled(!inProgress);
        }
    }

    private void launchImagePicker() {
        pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void persistReadPermission(@NonNull Uri uri) {
        if (getContext() == null) return;
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    private interface DatePickedCallback {
        void onPicked(long millis);
    }

    private interface TimePickedCallback {
        void onPicked(int hourOfDay, int minute);
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

    private void openTimePicker(long initialMillis, @NonNull TimePickedCallback callback) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(initialMillis > 0 ? initialMillis : System.currentTimeMillis());
        new TimePickerDialog(
                requireContext(),
                (picker, hourOfDay, minute) -> callback.onPicked(hourOfDay, minute),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void initializeDefaultRegistrationWindow() {
        Calendar opens = Calendar.getInstance();
        opens.set(Calendar.HOUR_OF_DAY, 0);
        opens.set(Calendar.MINUTE, 0);
        opens.set(Calendar.SECOND, 0);
        opens.set(Calendar.MILLISECOND, 0);
        registrationOpensMillis = opens.getTimeInMillis();

        Calendar closes = (Calendar) opens.clone();
        closes.add(Calendar.DAY_OF_MONTH, 4);
        closes.set(Calendar.HOUR_OF_DAY, 23);
        closes.set(Calendar.MINUTE, 59);
        registrationClosesMillis = closes.getTimeInMillis();
    }

    private void initializeDefaultEventWindow() {
        Calendar eventStart = Calendar.getInstance();
        eventStart.add(Calendar.DAY_OF_MONTH, 7);
        eventStart.set(Calendar.HOUR_OF_DAY, 18);
        eventStart.set(Calendar.MINUTE, 0);
        eventStart.set(Calendar.SECOND, 0);
        eventStart.set(Calendar.MILLISECOND, 0);
        eventStartMillis = eventStart.getTimeInMillis();

        Calendar eventEnd = (Calendar) eventStart.clone();
        eventEnd.add(Calendar.HOUR_OF_DAY, 1);
        eventEndMillis = eventEnd.getTimeInMillis();
    }

    private void updateRegistrationWindowViews(
            @NonNull TextView registrationStartDateView,
            @NonNull TextView registrationEndDateView,
            @NonNull TextView registrationEndTimeView
    ) {
        bindChipValue(registrationStartDateView, registrationOpensMillis, dateFormat, "Start date");
        bindChipValue(registrationEndDateView, registrationClosesMillis, dateFormat, "End date");
        bindChipValue(registrationEndTimeView, registrationClosesMillis, timeFormat, "End time");
    }

    private void updateEventWindowViews(
            @NonNull TextView eventDateView,
            @NonNull TextView eventStartTimeView,
            @NonNull TextView eventEndTimeView
    ) {
        bindChipValue(eventDateView, eventStartMillis, dateFormat, "Event date");
        bindChipValue(eventStartTimeView, eventStartMillis, timeFormat, "Start time");
        bindChipValue(eventEndTimeView, eventEndMillis, timeFormat, "End time");
    }

    private void bindChipValue(
            @NonNull TextView chipView,
            long millis,
            @NonNull SimpleDateFormat formatter,
            @NonNull String placeholder
    ) {
        if (millis > 0) {
            chipView.setText(formatter.format(new Date(millis)));
            updateDateChipStyle(chipView, true);
            return;
        }
        chipView.setText(placeholder);
        updateDateChipStyle(chipView, false);
    }

    private long updateDatePortion(long existingMillis, long selectedDateMillis, int defaultHour, int defaultMinute) {
        Calendar existing = Calendar.getInstance();
        boolean hasExisting = existingMillis > 0;
        existing.setTimeInMillis(hasExisting ? existingMillis : System.currentTimeMillis());

        Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(selectedDateMillis > 0 ? selectedDateMillis : System.currentTimeMillis());

        Calendar merged = Calendar.getInstance();
        merged.set(
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
                hasExisting ? existing.get(Calendar.HOUR_OF_DAY) : defaultHour,
                hasExisting ? existing.get(Calendar.MINUTE) : defaultMinute,
                0
        );
        merged.set(Calendar.MILLISECOND, 0);
        return merged.getTimeInMillis();
    }

    private long updateTimePortion(long existingMillis, int hourOfDay, int minute, long fallbackDateMillis) {
        Calendar base = Calendar.getInstance();
        long sourceMillis = existingMillis > 0 ? existingMillis : fallbackDateMillis;
        base.setTimeInMillis(sourceMillis > 0 ? sourceMillis : System.currentTimeMillis());
        base.set(Calendar.HOUR_OF_DAY, hourOfDay);
        base.set(Calendar.MINUTE, minute);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);
        return base.getTimeInMillis();
    }

    private void bindExistingGeofence(
            @NonNull Event event,
            @NonNull EditText geofenceLatitudeInput,
            @NonNull EditText geofenceLongitudeInput,
            @NonNull EditText geofenceRadiusInput
    ) {
        if (event.getGeofenceCenter() != null) {
            geofenceLatitudeInput.setText(formatCoordinate(event.getGeofenceCenter().getLatitude()));
            geofenceLongitudeInput.setText(formatCoordinate(event.getGeofenceCenter().getLongitude()));
        }
        if (event.getGeofenceRadiusMeters() != null && event.getGeofenceRadiusMeters() > 0) {
            geofenceRadiusInput.setText(String.valueOf(event.getGeofenceRadiusMeters()));
        }
        // Show preview button if coordinates are already set
        if (getView() != null && event.getGeofenceCenter() != null) {
            MaterialButton previewBtn = getView().findViewById(R.id.button_preview_geofence);
            if (previewBtn != null) previewBtn.setVisibility(View.VISIBLE);
        }
    }

    private void updateGeofenceSectionVisibility(@NonNull View geofenceFields, boolean visible) {
        geofenceFields.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updateManualCoordinatesVisibility(
            @NonNull View manualCoordinatesFields,
            @NonNull MaterialButton toggleButton,
            boolean expanded
    ) {
        manualCoordinatesFields.setVisibility(expanded ? View.VISIBLE : View.GONE);
        toggleButton.setText(expanded
                ? "Hide Manual Coordinates"
                : "Show Manual Coordinates");
    }

    private void updateDateChipStyle(@NonNull TextView textView, boolean hasValue) {
        int color = MaterialColors.getColor(
                textView,
                hasValue
                        ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorOutline
        );
        textView.setTextColor(color);
    }

    private void requestCurrentLocationForGeofence(
            @NonNull EditText geofenceLatitudeInput,
            @NonNull EditText geofenceLongitudeInput,
            @NonNull EditText locationNameInput
    ) {
        if (!isAdded()) {
            return;
        }
        pendingLocationAction = () -> fetchCurrentLocationForGeofence(
                geofenceLatitudeInput,
                geofenceLongitudeInput,
                locationNameInput
        );
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
                    HeliosUi.toast(this, HeliosLocation.buildLocationServicesDisabledMessage(
                            "use the current device position"
                    ));
                });
    }

    private void handleLocationPermissionResult(@NonNull Map<String, Boolean> grantResults) {
        if (!isAdded()) {
            return;
        }
        if (!HeliosLocation.hasAnyLocationPermission(requireContext())) {
            clearPendingLocationAction();
            HeliosUi.toast(this, HeliosLocation.buildPermissionDeniedMessage(
                    "use the current device position"
            ));
            return;
        }
        requestLocationAccessAndServices();
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
        HeliosUi.toast(this, HeliosLocation.buildLocationServicesDisabledMessage(
                "use the current device position"
        ));
    }

    private void fetchCurrentLocationForGeofence(
            @NonNull EditText geofenceLatitudeInput,
            @NonNull EditText geofenceLongitudeInput,
            @NonNull EditText locationNameInput
    ) {
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
                            applyCurrentLocationToFields(
                                    location.getLatitude(),
                                    location.getLongitude(),
                                    geofenceLatitudeInput,
                                    geofenceLongitudeInput,
                                    locationNameInput
                            );
                            return;
                        }
                        fetchLastKnownLocationForGeofence(
                                geofenceLatitudeInput,
                                geofenceLongitudeInput,
                                locationNameInput
                        );
                    })
                    .addOnFailureListener(error -> {
                        if (!isAdded()) return;
                        fetchLastKnownLocationForGeofence(
                                geofenceLatitudeInput,
                                geofenceLongitudeInput,
                                locationNameInput
                        );
                    });
        } catch (SecurityException error) {
            HeliosUi.toast(this, "Could not read current location: " + error.getMessage());
        }
    }

    private void fetchLastKnownLocationForGeofence(
            @NonNull EditText geofenceLatitudeInput,
            @NonNull EditText geofenceLongitudeInput,
            @NonNull EditText locationNameInput
    ) {
        if (!isAdded() || locationClient == null) {
            return;
        }
        try {
            locationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (!isAdded()) return;
                        if (location == null) {
                            HeliosUi.toast(this, HeliosLocation.buildLocationUnavailableMessage(
                                    "use the current device position"
                            ));
                            return;
                        }
                        applyCurrentLocationToFields(
                                location.getLatitude(),
                                location.getLongitude(),
                                geofenceLatitudeInput,
                                geofenceLongitudeInput,
                                locationNameInput
                        );
                    })
                    .addOnFailureListener(error -> {
                        if (!isAdded()) return;
                        HeliosUi.toast(this, HeliosLocation.buildLocationUnavailableMessage(
                                "use the current device position"
                        ));
                    });
        } catch (SecurityException error) {
            HeliosUi.toast(this, "Could not read current location: " + error.getMessage());
        }
    }

    private void applyCurrentLocationToFields(
            double latitude,
            double longitude,
            @NonNull EditText geofenceLatitudeInput,
            @NonNull EditText geofenceLongitudeInput,
            @NonNull EditText locationNameInput
    ) {
        geofenceLatitudeInput.setText(formatCoordinate(latitude));
        geofenceLongitudeInput.setText(formatCoordinate(longitude));
        if (TextUtils.isEmpty(locationNameInput.getText())) {
            locationNameInput.setText("Pinned event location");
        }
        clearPendingLocationAction();
        // Show the manual coordinates section so the user can see the filled values
        if (getView() != null) {
            LinearLayout manualFields = getView().findViewById(R.id.layout_manual_coordinates_fields);
            MaterialButton toggleBtn = getView().findViewById(R.id.button_toggle_manual_coordinates);
            if (manualFields != null && toggleBtn != null && manualFields.getVisibility() != View.VISIBLE) {
                updateManualCoordinatesVisibility(manualFields, toggleBtn, true);
            }
        }
        HeliosUi.toast(this, "Geofence center set. Adjust the radius and preview on map if needed.");
    }

    private void updatePreviewButtonVisibility(
            @NonNull MaterialButton previewButton,
            @NonNull EditText latInput,
            @NonNull EditText lngInput
    ) {
        String lat = latInput.getText() != null ? latInput.getText().toString().trim() : "";
        String lng = lngInput.getText() != null ? lngInput.getText().toString().trim() : "";
        boolean hasCoords = !lat.isEmpty() && !lng.isEmpty();
        previewButton.setVisibility(hasCoords ? View.VISIBLE : View.GONE);
    }

    private void openGeofencePreview(
            @NonNull EditText latInput,
            @NonNull EditText lngInput
    ) {
        if (!isAdded()) return;
        String latStr = latInput.getText() != null ? latInput.getText().toString().trim() : "";
        String lngStr = lngInput.getText() != null ? lngInput.getText().toString().trim() : "";
        if (latStr.isEmpty() || lngStr.isEmpty()) {
            HeliosUi.toast(this, "Enter coordinates first.");
            return;
        }
        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            Uri geoUri = Uri.parse("geo:" + lat + "," + lng
                    + "?q=" + lat + "," + lng + "(Event+Location)");
            Intent intent = new Intent(Intent.ACTION_VIEW, geoUri);
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
            intent.setPackage(null);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                HeliosUi.toast(this, "No maps app available.");
            }
        } catch (NumberFormatException e) {
            HeliosUi.toast(this, "Invalid coordinates — enter valid numbers.");
        }
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

    @NonNull
    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.6f", value);
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

    private void updateCreatePrimaryButtonLabel(@NonNull MaterialButton primaryButton) {
        primaryButton.setText(isPrivateEvent ? "Create Private Event" : "Create Event");
    }

    private void updatePrivateToggle(@NonNull TextView on, @NonNull TextView off, boolean isPrivate) {
        if (isPrivate) {
            on.setBackgroundResource(R.drawable.bg_toggle_left_active);
            off.setBackgroundResource(R.drawable.bg_toggle_right_inactive);
        } else {
            on.setBackgroundResource(R.drawable.bg_toggle_left_inactive);
            off.setBackgroundResource(R.drawable.bg_toggle_right_active);
        }
    }
}
