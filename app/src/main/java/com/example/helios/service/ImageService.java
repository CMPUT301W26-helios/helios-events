package com.example.helios.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.helios.data.ImageRepository;
import com.example.helios.model.Event;
import com.example.helios.model.ImageAsset;
import com.example.helios.ui.common.HeliosImageUploader;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Service for admin-side image management.
 *
 * Role: thin service layer for listing events with poster images and
 * coordinating Firebase Storage cleanup with image metadata updates.
 */
public class ImageService {

    private final Context applicationContext;
    private final ImageRepository repository;

    public ImageService(@NonNull Context context, @NonNull ImageRepository repository) {
        this.applicationContext = context.getApplicationContext();
        this.repository = repository;
    }

    /**
     * Retrieves all events that have a non-empty posterImageId.
     *
     * @param onSuccess Callback receiving the filtered list of Events.
     * @param onFailure Callback for failed operation.
     */
    public void getAllImages(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getEventsWithPosters(onSuccess, onFailure);
    }

    public void saveImageAsset(
            @NonNull ImageAsset imageAsset,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.saveImageAsset(imageAsset, onSuccess, onFailure);
    }

    public void getImageAssetForEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<ImageAsset> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.getImageAssetForEvent(eventId, onSuccess, onFailure);
    }

    public void deleteImageAsset(
            @NonNull String imageId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        repository.deleteImageAsset(imageId, onSuccess, onFailure);
    }

    /**
     * Removes the poster from an event by nulling its posterImageId field.
     *
     * @param event     The event whose poster should be removed.
     * @param onSuccess Callback for successful operation.
     * @param onFailure Callback for failed operation.
     */
    public void deleteImage(
            @NonNull Event event,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("eventId must not be empty."));
            return;
        }
        repository.getImageAssetForEvent(eventId, imageAsset -> {
            if (imageAsset == null || imageAsset.getStoragePath() == null
                    || imageAsset.getStoragePath().trim().isEmpty()) {
                repository.removeEventPoster(eventId, onSuccess, onFailure);
                return;
            }

            HeliosImageUploader.deleteFromStorage(
                    applicationContext,
                    imageAsset.getStoragePath(),
                    () -> deleteImageRecordAndClearPoster(eventId, imageAsset, onSuccess, onFailure),
                    onFailure
            );
        }, onFailure);
    }

    private void deleteImageRecordAndClearPoster(
            @NonNull String eventId,
            @NonNull ImageAsset imageAsset,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        String imageId = imageAsset.getImageId();
        if (imageId == null || imageId.trim().isEmpty()) {
            repository.removeEventPoster(eventId, onSuccess, onFailure);
            return;
        }

        repository.deleteImageAsset(
                imageId,
                unused -> repository.removeEventPoster(eventId, onSuccess, onFailure),
                onFailure
        );
    }
}
