package com.example.helios.ui.nav;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.model.Event;
import com.example.helios.model.WaitingListEntry;
import com.example.helios.service.EventService;
import com.example.helios.service.WaitingListService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class EntrantMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String ARG_EVENT_ID = "arg_event_id";
    private static final int FENCE_STROKE_COLOR = 0xFFD32F2F;
    private static final int FENCE_FILL_COLOR = 0x22D32F2F;

    private EventService eventService;
    private WaitingListService waitingListService;
    private final List<MarkerPoint> markerPoints = new ArrayList<>();

    @Nullable
    private String eventId;
    @Nullable
    private Event loadedEvent;
    @Nullable
    private GoogleMap googleMap;
    @Nullable
    private ProgressBar progressBar;
    @Nullable
    private TextView emptyView;
    @Nullable
    private TextView helperView;
    @Nullable
    private View mapContainer;
    @Nullable
    private MaterialButton externalMapButton;
    private boolean mapsConfigured;

    public EntrantMapFragment() {
        super(R.layout.fragment_entrant_map);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eventService = HeliosApplication.from(requireContext()).getEventService();
        waitingListService = HeliosApplication.from(requireContext()).getWaitingListService();
        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString(ARG_EVENT_ID);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView titleView = view.findViewById(R.id.submenu_title);
        TextView subtitleView = view.findViewById(R.id.submenu_subtitle);
        progressBar = view.findViewById(R.id.progress_entrant_map);
        emptyView = view.findViewById(R.id.text_entrant_map_empty);
        helperView = view.findViewById(R.id.text_entrant_map_helper);
        mapContainer = view.findViewById(R.id.entrant_map_container);
        externalMapButton = view.findViewById(R.id.button_open_entrant_map_external);

        MaterialButton backButton = view.findViewById(R.id.submenu_back_button);
        titleView.setText("Entrant Map");
        subtitleView.setText("Review where entrants joined the waiting list.");
        backButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        if (externalMapButton != null) {
            externalMapButton.setOnClickListener(v -> openMapExternally());
            externalMapButton.setEnabled(false);
        }

        if (eventId == null || eventId.trim().isEmpty()) {
            toast("Missing event id.");
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        mapsConfigured = hasConfiguredMapsApiKey();
        if (mapsConfigured) {
            ensureMapFragment();
        }
        updateMapHelperText();
        loadEvent();
        loadEntrantLocations();
    }

    private void loadEvent() {
        eventService.getEventById(eventId, event -> {
            if (!isAdded()) {
                return;
            }
            loadedEvent = event;
            renderMarkersIfReady();
        }, error -> {
            if (!isAdded()) {
                return;
            }
            toast("Failed to load event map details: " + error.getMessage());
        });
    }

    private void ensureMapFragment() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.entrant_map_container);
        if (fragment instanceof SupportMapFragment) {
            ((SupportMapFragment) fragment).getMapAsync(this);
            return;
        }

        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.entrant_map_container, mapFragment)
                .commitNow();
        mapFragment.getMapAsync(this);
    }

    private void loadEntrantLocations() {
        setLoading(true);
        waitingListService.getEntriesForEvent(eventId, entries -> {
            if (!isAdded()) {
                return;
            }

            markerPoints.clear();
            for (WaitingListEntry entry : entries) {
                if (entry == null || entry.getJoinLatitude() == null || entry.getJoinLongitude() == null) {
                    continue;
                }

                String title = entry.getEntrantUid();
                if (title == null || title.trim().isEmpty()) {
                    title = "Entrant";
                }

                markerPoints.add(new MarkerPoint(
                        new LatLng(entry.getJoinLatitude(), entry.getJoinLongitude()),
                        title
                ));
            }

            setLoading(false);
            renderMarkersIfReady();
        }, error -> {
            if (!isAdded()) {
                return;
            }
            setLoading(false);
            showEmptyState("Failed to load entrant locations.");
            toast("Failed to load entrant locations: " + error.getMessage());
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        renderMarkersIfReady();
    }

    private void renderMarkersIfReady() {
        if (!isAdded()) {
            return;
        }

        boolean hasFence = loadedEvent != null && loadedEvent.hasGeofence();
        LatLng eventCenter = getEventCenter();

        if (markerPoints.isEmpty() && !hasFence) {
            showEmptyState("No entrant locations or event fence have been captured for this event.");
            updateMapHelperText();
            return;
        }

        if (!mapsConfigured || googleMap == null) {
            showEmptyState("Embedded map unavailable in this build. Use Open in Maps to view the event area.");
            updateMapHelperText();
            return;
        }

        googleMap.clear();
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (mapContainer != null) {
            mapContainer.setVisibility(View.VISIBLE);
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasBoundsTarget = false;

        if (hasFence && eventCenter != null) {
            googleMap.addMarker(new MarkerOptions()
                    .position(eventCenter)
                    .title(resolveEventCenterTitle()));
            googleMap.addCircle(new CircleOptions()
                    .center(eventCenter)
                    .radius(loadedEvent.getGeofenceRadiusMeters())
                    .strokeColor(FENCE_STROKE_COLOR)
                    .fillColor(FENCE_FILL_COLOR)
                    .strokeWidth(4f));
            includeFenceBounds(builder, eventCenter, loadedEvent.getGeofenceRadiusMeters());
            hasBoundsTarget = true;
        }

        for (MarkerPoint point : markerPoints) {
            googleMap.addMarker(new MarkerOptions()
                    .position(point.latLng)
                    .title(point.title));
            builder.include(point.latLng);
            hasBoundsTarget = true;
        }

        if (!hasBoundsTarget) {
            showEmptyState("No entrant locations or event fence have been captured for this event.");
            updateMapHelperText();
            return;
        }

        if (markerPoints.size() == 1 && !hasFence) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerPoints.get(0).latLng, 13f));
            updateMapHelperText();
            return;
        }

        if (markerPoints.isEmpty() && hasFence && eventCenter != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(eventCenter, getFenceZoomLevel()));
            updateMapHelperText();
            return;
        }

        if (mapContainer != null && hasBoundsTarget) {
            mapContainer.post(() -> {
                if (googleMap == null || !isAdded()) {
                    return;
                }
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(builder.build(), 160)
                );
            });
        }

        updateMapHelperText();
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loading && emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (!loading) {
            updateMapHelperText();
        }
    }

    private void showEmptyState(@NonNull String message) {
        if (mapContainer != null) {
            mapContainer.setVisibility(View.GONE);
        }
        if (emptyView != null) {
            emptyView.setText(message);
            emptyView.setVisibility(View.VISIBLE);
        }
        updateMapHelperText();
    }

    private void updateMapHelperText() {
        boolean hasMarkers = !markerPoints.isEmpty();
        boolean hasFence = loadedEvent != null && loadedEvent.hasGeofence();
        boolean canOpenMap = hasMarkers || hasFence;

        if (externalMapButton != null) {
            externalMapButton.setEnabled(canOpenMap);
            externalMapButton.setAlpha(canOpenMap ? 1f : 0.5f);
        }

        if (helperView == null) {
            return;
        }

        if (!hasMarkers && !hasFence) {
            helperView.setText("Entrants who join with captured geolocation will appear here once the event fence is configured.");
            return;
        }

        if (!mapsConfigured) {
            helperView.setText("This build is missing a Google Maps API key. Open the map externally until MAPS_API_KEY is configured.");
            return;
        }

        if (hasFence && hasMarkers) {
            helperView.setText("The red circle shows the event fence and markers show where entrants joined.");
            return;
        }
        if (hasFence) {
            helperView.setText("The red circle shows the event fence. Entrant markers will appear after people join.");
            return;
        }
        helperView.setText("Open the current entrant area in your maps app if you need a full-screen view.");
    }

    private void openMapExternally() {
        if (!isAdded()) {
            return;
        }
        if (markerPoints.isEmpty() && getEventCenter() == null) {
            toast("No event map location is available yet.");
            return;
        }

        Intent preferredIntent = new Intent(Intent.ACTION_VIEW, buildGeoMapUri());
        preferredIntent.setPackage("com.google.android.apps.maps");
        if (preferredIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(preferredIntent);
            return;
        }

        Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, buildBrowserMapUri());
        if (fallbackIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            try {
                startActivity(fallbackIntent);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall through to the toast below.
            }
        }

        toast("No maps app is available on this device.");
    }

    @NonNull
    private Uri buildGeoMapUri() {
        if (markerPoints.size() == 1) {
            MarkerPoint point = markerPoints.get(0);
            return Uri.parse(
                    "geo:" + point.latLng.latitude + "," + point.latLng.longitude
                            + "?q=" + point.latLng.latitude + "," + point.latLng.longitude
                            + "(" + Uri.encode(point.title) + ")"
            );
        }

        LatLng center = getMapCenter();
        return Uri.parse(
                "geo:" + center.latitude + "," + center.longitude
                        + "?q=" + center.latitude + "," + center.longitude
                        + "(" + Uri.encode("Entrant map area") + ")"
        );
    }

    @NonNull
    private Uri buildBrowserMapUri() {
        if (markerPoints.size() == 1) {
            MarkerPoint point = markerPoints.get(0);
            return Uri.parse(
                    "https://www.google.com/maps/search/?api=1&query="
                            + point.latLng.latitude + "," + point.latLng.longitude
            );
        }

        LatLng center = getMapCenter();
        return Uri.parse(
                "https://www.google.com/maps/search/?api=1&query="
                        + center.latitude + "," + center.longitude
        );
    }

    @NonNull
    private LatLng getMapCenter() {
        LatLng eventCenter = getEventCenter();
        if (markerPoints.size() == 1) {
            return markerPoints.get(0).latLng;
        }
        if (markerPoints.isEmpty() && eventCenter != null) {
            return eventCenter;
        }

        double minLatitude = Double.MAX_VALUE;
        double maxLatitude = -Double.MAX_VALUE;
        double minLongitude = Double.MAX_VALUE;
        double maxLongitude = -Double.MAX_VALUE;

        for (MarkerPoint point : markerPoints) {
            minLatitude = Math.min(minLatitude, point.latLng.latitude);
            maxLatitude = Math.max(maxLatitude, point.latLng.latitude);
            minLongitude = Math.min(minLongitude, point.latLng.longitude);
            maxLongitude = Math.max(maxLongitude, point.latLng.longitude);
        }

        return new LatLng(
                (minLatitude + maxLatitude) / 2d,
                (minLongitude + maxLongitude) / 2d
        );
    }

    @Nullable
    private LatLng getEventCenter() {
        if (loadedEvent == null || loadedEvent.getGeofenceCenter() == null) {
            return null;
        }
        return new LatLng(
                loadedEvent.getGeofenceCenter().getLatitude(),
                loadedEvent.getGeofenceCenter().getLongitude()
        );
    }

    private void includeFenceBounds(
            @NonNull LatLngBounds.Builder builder,
            @NonNull LatLng center,
            @Nullable Integer radiusMeters
    ) {
        if (radiusMeters == null || radiusMeters <= 0) {
            builder.include(center);
            return;
        }
        double latitudeDelta = radiusMeters / 111_320d;
        double longitudeDelta = radiusMeters / (111_320d * Math.cos(Math.toRadians(center.latitude)));
        builder.include(new LatLng(center.latitude + latitudeDelta, center.longitude));
        builder.include(new LatLng(center.latitude - latitudeDelta, center.longitude));
        builder.include(new LatLng(center.latitude, center.longitude + longitudeDelta));
        builder.include(new LatLng(center.latitude, center.longitude - longitudeDelta));
    }

    @NonNull
    private String resolveEventCenterTitle() {
        if (loadedEvent == null) {
            return "Event geofence";
        }
        String locationName = loadedEvent.getLocationName();
        if (locationName != null && !locationName.trim().isEmpty()) {
            return locationName;
        }
        return "Event geofence";
    }

    private float getFenceZoomLevel() {
        if (loadedEvent == null || loadedEvent.getGeofenceRadiusMeters() == null) {
            return 13f;
        }
        int radiusMeters = loadedEvent.getGeofenceRadiusMeters();
        if (radiusMeters <= 200) return 16f;
        if (radiusMeters <= 500) return 15f;
        if (radiusMeters <= 1_500) return 14f;
        if (radiusMeters <= 5_000) return 12f;
        return 10f;
    }

    private boolean hasConfiguredMapsApiKey() {
        try {
            ApplicationInfo applicationInfo = requireContext().getPackageManager()
                    .getApplicationInfo(requireContext().getPackageName(), PackageManager.GET_META_DATA);
            if (applicationInfo.metaData == null) {
                return false;
            }

            String apiKey = applicationInfo.metaData.getString("com.google.android.geo.API_KEY");
            if (apiKey == null) {
                return false;
            }
            // DO NOT REMOVE!!!
            // API_KEY
            // com.google.android.geo.API_KEY
            // OR: MAPS_API_KEY
            //toast(apiKey);

            String normalized = apiKey.trim();
            return !normalized.isEmpty()
                    && !"YOUR_KEY_HERE".equals(normalized)
                    && !normalized.startsWith("${");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void toast(@NonNull String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private static final class MarkerPoint {
        private final LatLng latLng;
        private final String title;

        private MarkerPoint(@NonNull LatLng latLng, @NonNull String title) {
            this.latLng = latLng;
            this.title = title;
        }
    }
}
