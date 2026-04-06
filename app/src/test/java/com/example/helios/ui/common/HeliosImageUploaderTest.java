package com.example.helios.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.firebase.storage.StorageException;

import org.junit.Test;

public class HeliosImageUploaderTest {

    @Test
    public void normalizeConfiguredBucket_addsGsSchemeForRawBucketName() {
        String normalized = HeliosImageUploader.normalizeConfiguredBucket(
                "helios-testdb.firebasestorage.app"
        );

        assertEquals("gs://helios-testdb.firebasestorage.app", normalized);
    }

    @Test
    public void normalizeConfiguredBucket_keepsExistingGsScheme() {
        String normalized = HeliosImageUploader.normalizeConfiguredBucket(
                "gs://helios-testdb.firebasestorage.app"
        );

        assertEquals("gs://helios-testdb.firebasestorage.app", normalized);
    }

    @Test
    public void normalizeConfiguredBucket_rejectsBlankValue() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> HeliosImageUploader.normalizeConfiguredBucket("   ")
        );

        assertEquals("Firebase Storage bucket is missing from app configuration.", error.getMessage());
    }

    @Test
    public void mapStorageFailureCodeToMessage_returnsConfigMessageForMissingBucket() {
        String message = HeliosImageUploader.mapStorageFailureCodeToMessage(
                StorageException.ERROR_BUCKET_NOT_FOUND,
                HeliosImageUploader.UploadStage.PUT_FILE
        );

        assertEquals("Firebase Storage is not configured for this project.", message);
    }

    @Test
    public void mapStorageFailureCodeToMessage_returnsAuthMessageForUnauthorizedUpload() {
        String message = HeliosImageUploader.mapStorageFailureCodeToMessage(
                StorageException.ERROR_NOT_AUTHORIZED,
                HeliosImageUploader.UploadStage.PUT_FILE
        );

        assertEquals("You do not have permission to upload images.", message);
    }

    @Test
    public void mapStorageFailureCodeToMessage_returnsQuotaMessageWhenBillingBlocksUpload() {
        String message = HeliosImageUploader.mapStorageFailureCodeToMessage(
                StorageException.ERROR_QUOTA_EXCEEDED,
                HeliosImageUploader.UploadStage.PUT_FILE
        );

        assertEquals("Firebase Storage quota or billing is blocking uploads.", message);
    }

    @Test
    public void mapStorageFailureCodeToMessage_returnsDownloadResolutionMessageForMissingObjectAfterUpload() {
        String message = HeliosImageUploader.mapStorageFailureCodeToMessage(
                StorageException.ERROR_OBJECT_NOT_FOUND,
                HeliosImageUploader.UploadStage.GET_DOWNLOAD_URL
        );

        assertEquals(
                "Upload finished but the file could not be resolved for download. Verify the Firebase Storage bucket and rules.",
                message
        );
    }

    @Test
    public void mapStorageFailureCodeToMessage_returnsGenericMessageForMissingObjectDuringPutFile() {
        String message = HeliosImageUploader.mapStorageFailureCodeToMessage(
                StorageException.ERROR_OBJECT_NOT_FOUND,
                HeliosImageUploader.UploadStage.PUT_FILE
        );

        assertEquals(
                "Firebase Storage rejected the upload location. Verify the configured bucket exists and matches this app.",
                message
        );
    }

    @Test
    public void mapStorageFailureCodeToMessage_returnsGenericMessageForUnknownError() {
        String message = HeliosImageUploader.mapStorageFailureCodeToMessage(
                StorageException.ERROR_UNKNOWN,
                null
        );

        assertEquals("Image upload failed.", message);
    }
}
