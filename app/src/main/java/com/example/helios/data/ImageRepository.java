package com.example.helios.data;

import androidx.annotation.NonNull;

import com.example.helios.model.Event;
import com.example.helios.model.ImageAsset;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

/**
 * Repository interface for image/poster persistence operations.
 */
public interface ImageRepository {

    void getEventsWithPosters(
            @NonNull OnSuccessListener<List<Event>> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void removeEventPoster(
            @NonNull String eventId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void saveImageAsset(
            @NonNull ImageAsset imageAsset,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void getImageAssetForEvent(
            @NonNull String eventId,
            @NonNull OnSuccessListener<ImageAsset> onSuccess,
            @NonNull OnFailureListener onFailure
    );

    void deleteImageAsset(
            @NonNull String imageId,
            @NonNull OnSuccessListener<Void> onSuccess,
            @NonNull OnFailureListener onFailure
    );
}
