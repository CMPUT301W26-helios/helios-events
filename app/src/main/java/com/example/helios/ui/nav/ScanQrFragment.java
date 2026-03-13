package com.example.helios.ui.nav;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.helios.R;
import com.example.helios.ui.event.EventDetailsBottomSheet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment that provides QR code scanning functionality.
 * Supports scanning via the device camera or by uploading an image from the gallery.
 */
public class ScanQrFragment extends Fragment {

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private PreviewView viewFinder;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    /**
     * Default constructor for ScanQrFragment.
     */
    public ScanQrFragment() {
        super(R.layout.fragment_scan_qr);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        handleQrFromUri(imageUri);
                    }
                }
        );

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(getContext(), "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewFinder = view.findViewById(R.id.view_finder);

        view.findViewById(R.id.ll_upload_view).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        view.findViewById(R.id.btn_toggle_upload).setOnClickListener(v -> {
            stopCamera();
            updateToggleState(view, true);
        });

        view.findViewById(R.id.btn_toggle_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
            updateToggleState(view, false);
        });
    }

    /**
     * Updates the UI state when switching between camera scan and image upload modes.
     *
     * @param view     The fragment's root view.
     * @param isUpload True if switching to upload mode, false for camera mode.
     */
    private void updateToggleState(View view, boolean isUpload) {
        view.findViewById(R.id.btn_toggle_upload).setBackgroundResource(isUpload ? R.drawable.bg_toggle_left_active : R.drawable.bg_toggle_left_inactive);
        view.findViewById(R.id.btn_toggle_camera).setBackgroundResource(isUpload ? R.drawable.bg_toggle_right_inactive : R.drawable.bg_toggle_right_active);
        
        view.findViewById(R.id.iv_upload_check).setVisibility(isUpload ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.iv_camera_check).setVisibility(isUpload ? View.GONE : View.VISIBLE);
        
        view.findViewById(R.id.ll_upload_view).setVisibility(isUpload ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.view_finder).setVisibility(isUpload ? View.GONE : View.VISIBLE);
    }

    /**
     * Initializes and starts the CameraX provider.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(getContext(), "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    /**
     * Binds the camera preview and image analysis (barcode scanning) use cases to the lifecycle.
     */
    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        BarcodeScanner scanner = BarcodeScanning.getClient();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

            scanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null) {
                                requireActivity().runOnUiThread(() -> {
                                    handleQrCode(rawValue);
                                    stopCamera(); // Stop after first detection
                                });
                            }
                        }
                    })
                    .addOnCompleteListener(task -> image.close());
        });

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to bind camera", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Stops the camera and unbinds all use cases.
     */
    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    /**
     * Processes a QR code from a gallery image URI using ML Kit.
     *
     * @param uri The URI of the image to scan.
     */
    private void handleQrFromUri(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), uri);
            BarcodeScanner scanner = BarcodeScanning.getClient();
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (barcodes.isEmpty()) {
                            Toast.makeText(getContext(), "No QR code found in image", Toast.LENGTH_SHORT).show();
                        } else {
                            handleQrCode(barcodes.get(0).getRawValue());
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to scan image", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handles the detected QR code value by opening the event details bottom sheet.
     *
     * @param value The raw string value encoded in the QR code.
     */
    private void handleQrCode(String value) {
        if (!isAdded()) return;
        EventDetailsBottomSheet.newInstance(value)
                .show(getParentFragmentManager(), "event_details");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
