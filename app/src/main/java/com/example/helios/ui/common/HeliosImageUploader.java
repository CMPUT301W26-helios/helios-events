package com.example.helios.ui.common;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class HeliosImageUploader {
    private static final String TAG = "HeliosStorage";

    public static final class UploadResult {
        private final String downloadUrl;
        private final String storagePath;

        public UploadResult(@NonNull String downloadUrl, @NonNull String storagePath) {
            this.downloadUrl = downloadUrl;
            this.storagePath = storagePath;
        }

        @NonNull
        public String getDownloadUrl() {
            return downloadUrl;
        }

        @NonNull
        public String getStoragePath() {
            return storagePath;
        }
    }

    enum UploadStage {
        PUT_FILE,
        GET_DOWNLOAD_URL
    }

    private static final class StorageConfig {
        private final FirebaseStorage storage;
        private final String bucketUrl;

        private StorageConfig(@NonNull FirebaseStorage storage, @NonNull String bucketUrl) {
            this.storage = storage;
            this.bucketUrl = bucketUrl;
        }
    }

    public static final class ImageUploadException extends Exception {
        private final String userMessage;
        @Nullable private final UploadStage stage;
        @Nullable private final String bucketUrl;
        @Nullable private final String storagePath;

        private ImageUploadException(
                @NonNull String userMessage,
                @Nullable UploadStage stage,
                @Nullable String bucketUrl,
                @Nullable String storagePath,
                @NonNull Throwable cause
        ) {
            super(cause);
            this.userMessage = userMessage;
            this.stage = stage;
            this.bucketUrl = bucketUrl;
            this.storagePath = storagePath;
        }

        @NonNull
        public String getUserMessage() {
            return userMessage;
        }

        @Nullable
        public UploadStage getStage() {
            return stage;
        }

        @Nullable
        public String getBucketUrl() {
            return bucketUrl;
        }

        @Nullable
        public String getStoragePath() {
            return storagePath;
        }
    }

    private HeliosImageUploader() {}

    public static void uploadImage(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull String storagePathPrefix,
            @NonNull OnSuccessListener<UploadResult> onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        ContentResolver contentResolver = context.getContentResolver();
        String contentType = contentResolver.getType(imageUri);
        String extension = resolveExtension(contentResolver, contentType, imageUri);
        String storagePath = storagePathPrefix + extension;
        final File stagedImageFile;
        try {
            stagedImageFile = stageImageForUpload(context, imageUri, extension);
        } catch (IOException | SecurityException error) {
            onFailure.onFailure(error);
            return;
        }

        final StorageConfig storageConfig;
        try {
            storageConfig = getStorageConfig(context);
        } catch (IllegalStateException error) {
            deleteQuietly(stagedImageFile);
            logNonStorageFailure(error, null, null);
            onFailure.onFailure(error);
            return;
        }

        StorageMetadata.Builder metadataBuilder = new StorageMetadata.Builder();
        if (contentType != null && !contentType.trim().isEmpty()) {
            metadataBuilder.setContentType(contentType);
        }

        StorageReference storageRef = storageConfig.storage.getReference().child(storagePath);
        storageRef.putFile(Uri.fromFile(stagedImageFile), metadataBuilder.build())
                .addOnSuccessListener(unused ->
                        storageRef.getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {
                                    deleteQuietly(stagedImageFile);
                                    onSuccess.onSuccess(new UploadResult(downloadUri.toString(), storagePath));
                                })
                                .addOnFailureListener(error -> {
                                    deleteQuietly(stagedImageFile);
                                    onFailure.onFailure(wrapUploadFailure(
                                            error,
                                            storageConfig.bucketUrl,
                                            storagePath,
                                            UploadStage.GET_DOWNLOAD_URL
                                    ));
                                }))
                .addOnFailureListener(error -> {
                    deleteQuietly(stagedImageFile);
                    onFailure.onFailure(wrapUploadFailure(
                            error,
                            storageConfig.bucketUrl,
                            storagePath,
                            UploadStage.PUT_FILE
                    ));
                });
    }

    public static void deleteFromStorage(
            @NonNull Context context,
            @NonNull String storageLocation,
            @NonNull Runnable onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        deleteFromResolvedReference(resolveStorageReference(context, storageLocation), onSuccess, onFailure);
    }

    @NonNull
    public static String getUserFacingUploadErrorMessage(@NonNull Exception error) {
        if (error instanceof ImageUploadException) {
            return ((ImageUploadException) error).getUserMessage();
        }
        if (error instanceof StorageException) {
            return mapStorageFailureCodeToMessage(
                    ((StorageException) error).getErrorCode(),
                    null
            );
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Image upload failed.";
        }
        return message;
    }

    @NonNull
    static String normalizeConfiguredBucket(@Nullable String configuredBucket) {
        if (configuredBucket == null || configuredBucket.trim().isEmpty()) {
            throw new IllegalStateException("Firebase Storage bucket is missing from app configuration.");
        }
        String normalized = configuredBucket.trim();
        if (normalized.startsWith("gs://")
                || normalized.startsWith("https://")
                || normalized.startsWith("http://")) {
            return normalized;
        }
        return "gs://" + normalized;
    }

    @NonNull
    static String mapStorageFailureCodeToMessage(int errorCode, @Nullable UploadStage stage) {
        if (errorCode == StorageException.ERROR_BUCKET_NOT_FOUND
                || errorCode == StorageException.ERROR_PROJECT_NOT_FOUND) {
            return "Firebase Storage is not configured for this project.";
        }
        if (errorCode == StorageException.ERROR_NOT_AUTHENTICATED
                || errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
            return "You do not have permission to upload images.";
        }
        if (errorCode == StorageException.ERROR_QUOTA_EXCEEDED) {
            return "Firebase Storage quota or billing is blocking uploads.";
        }
        if (errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
                && stage == UploadStage.GET_DOWNLOAD_URL) {
            return "Upload finished but the file could not be resolved for download. Verify the Firebase Storage bucket and rules.";
        }
        if (errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
                && stage == UploadStage.PUT_FILE) {
            return "Firebase Storage rejected the upload location. Verify the configured bucket exists and matches this app.";
        }
        return "Image upload failed.";
    }

    @NonNull
    private static String resolveExtension(
            @NonNull ContentResolver contentResolver,
            @Nullable String contentType,
            @NonNull Uri imageUri
    ) {
        String extension = null;
        if (contentType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
        }
        if (extension == null || extension.trim().isEmpty()) {
            extension = resolveExtensionFromDisplayName(contentResolver, imageUri);
        }
        if (extension == null || extension.trim().isEmpty()) {
            extension = MimeTypeMap.getFileExtensionFromUrl(imageUri.toString());
        }
        if (extension == null || extension.trim().isEmpty()) {
            return ".jpg";
        }
        extension = extension.trim().toLowerCase(Locale.US);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (extension.isEmpty()) {
            return ".jpg";
        }
        return "." + extension;
    }

    @NonNull
    private static File stageImageForUpload(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull String extension
    ) throws IOException {
        File stagedFile = File.createTempFile(
                "helios-upload-",
                extension,
                context.getCacheDir()
        );

        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Unable to open selected image.");
            }
            try (FileOutputStream outputStream = new FileOutputStream(stagedFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        } catch (IOException | RuntimeException error) {
            deleteQuietly(stagedFile);
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to read selected image.", error);
        }
        return stagedFile;
    }

    @NonNull
    private static StorageReference resolveStorageReference(
            @NonNull Context context,
            @NonNull String storageLocation
    ) {
        FirebaseStorage storage = getStorageConfig(context).storage;
        if (storageLocation.startsWith("gs://")
                || storageLocation.startsWith("https://")
                || storageLocation.startsWith("http://")) {
            try {
                return storage.getReferenceFromUrl(storageLocation);
            } catch (IllegalArgumentException ignored) {
                // Fall back to treating the value as a raw storage path if it is not a valid URL.
            }
        }
        return storage.getReference().child(storageLocation);
    }

    @NonNull
    private static StorageConfig getStorageConfig(@NonNull Context context) {
        String normalizedBucket = normalizeConfiguredBucket(
                context.getString(R.string.google_storage_bucket)
        );
        // Use the bucket declared by google-services so uploads target the same Storage bucket
        // across devices and environments instead of relying on the SDK default selection.
        return new StorageConfig(FirebaseStorage.getInstance(normalizedBucket), normalizedBucket);
    }

    private static void deleteFromResolvedReference(
            @NonNull StorageReference storageReference,
            @NonNull Runnable onSuccess,
            @NonNull OnFailureListener onFailure
    ) {
        storageReference.delete()
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(error -> {
                    if (isObjectMissing(error)) {
                        onSuccess.run();
                        return;
                    }
                    onFailure.onFailure(error);
                });
    }

    @NonNull
    private static Exception wrapUploadFailure(
            @NonNull Exception error,
            @NonNull String bucketUrl,
            @NonNull String storagePath,
            @NonNull UploadStage stage
    ) {
        if (!(error instanceof StorageException)) {
            logNonStorageFailure(error, bucketUrl, storagePath);
            return error;
        }

        StorageException storageError = (StorageException) error;
        String userMessage = mapStorageFailureCodeToMessage(storageError.getErrorCode(), stage);
        logStorageFailure(storageError, bucketUrl, storagePath, stage);
        return new ImageUploadException(
                userMessage,
                stage,
                bucketUrl,
                storagePath,
                storageError
        );
    }

    private static void logStorageFailure(
            @NonNull StorageException error,
            @NonNull String bucketUrl,
            @NonNull String storagePath,
            @NonNull UploadStage stage
    ) {
        Log.e(
                TAG,
                "bucket=" + bucketUrl
                        + ", path=" + storagePath
                        + ", stage=" + stage
                        + ", exception=" + error.getClass().getSimpleName()
                        + ", code=" + error.getErrorCode()
                        + ", message=" + String.valueOf(error.getMessage()),
                error
        );
    }

    private static void logNonStorageFailure(
            @NonNull Exception error,
            @Nullable String bucketUrl,
            @Nullable String storagePath
    ) {
        Log.e(
                TAG,
                "bucket=" + String.valueOf(bucketUrl)
                        + ", path=" + String.valueOf(storagePath)
                        + ", stage=LOCAL"
                        + ", exception=" + error.getClass().getSimpleName()
                        + ", message=" + String.valueOf(error.getMessage()),
                error
        );
    }

    private static boolean isObjectMissing(@NonNull Exception error) {
        if (error instanceof StorageException
                && ((StorageException) error).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
            return true;
        }
        String message = error.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.US);
        return normalized.contains("object does not exist")
                || normalized.contains("does not exist at location");
    }

    private static void deleteQuietly(@Nullable File file) {
        if (file == null || !file.exists()) {
            return;
        }
        // Cache copies are disposable and should not block the caller if cleanup fails.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Nullable
    private static String resolveExtensionFromDisplayName(
            @NonNull ContentResolver contentResolver,
            @NonNull Uri imageUri
    ) {
        try (Cursor cursor = contentResolver.query(
                imageUri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex < 0) {
                return null;
            }
            String displayName = cursor.getString(nameIndex);
            if (displayName == null) {
                return null;
            }
            int lastDot = displayName.lastIndexOf('.');
            if (lastDot < 0 || lastDot == displayName.length() - 1) {
                return null;
            }
            return displayName.substring(lastDot + 1);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
