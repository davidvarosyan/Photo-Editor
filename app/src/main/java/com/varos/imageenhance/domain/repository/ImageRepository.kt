package com.varos.imageenhance.domain.repository

import android.graphics.Bitmap
import android.net.Uri

/**
 * Loads images from the platform (gallery/camera URIs) and persists results.
 * Abstracted so the domain/presentation never touch ContentResolver/EXIF/
 * MediaStore directly, keeping Android framework concerns in the data layer.
 */
interface ImageRepository {
    /**
     * Decodes the image at [uri], corrects its orientation from EXIF and
     * downsamples it so the decoded bitmap has at most [maxPixels] pixels — a
     * memory bound that holds for any aspect ratio, including gigapixel images.
     */
    suspend fun loadBitmap(uri: Uri, maxPixels: Long): Bitmap

    /** Total pixel count of the image at [uri] (decodes bounds only — cheap). */
    suspend fun readPixelCount(uri: Uri): Long

    /** Saves [bitmap] to the device's shared Pictures collection, returning its Uri. */
    suspend fun saveToGallery(bitmap: Bitmap, displayName: String): Uri

    /**
     * Creates a writable file Uri (via FileProvider) for the camera to capture
     * a full-resolution photo into. The returned Uri is then passed back to
     * [loadBitmap] once capture succeeds.
     */
    fun createCaptureUri(): Uri
}
