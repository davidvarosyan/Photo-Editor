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
     * downsamples it so the longest edge is at most [maxEdge] px to bound
     * memory use on large photos.
     */
    suspend fun loadBitmap(uri: Uri, maxEdge: Int = 2048): Bitmap

    /** Saves [bitmap] to the device's shared Pictures collection, returning its Uri. */
    suspend fun saveToGallery(bitmap: Bitmap, displayName: String): Uri

    /**
     * Creates a writable file Uri (via FileProvider) for the camera to capture
     * a full-resolution photo into. The returned Uri is then passed back to
     * [loadBitmap] once capture succeeds.
     */
    fun createCaptureUri(): Uri
}
