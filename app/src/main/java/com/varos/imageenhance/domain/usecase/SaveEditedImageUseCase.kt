package com.varos.imageenhance.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.varos.imageenhance.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import androidx.core.graphics.scale

/**
 * Saves the already-rendered preview bitmap, **upscaled back to the original
 * image's resolution** so the saved file keeps the source's dimensions.
 *
 * Editing runs on a small working copy for responsiveness; here we scale that
 * exact result up (bilinear) to match the original pixel count, so the saved file
 * is full size and still visually identical to what the user previewed. The
 * target is capped by [maxPixels] so a gigapixel original can't OOM the upscale.
 * The temporary scaled bitmap is recycled after saving.
 */
class SaveEditedImageUseCase(
    private val repository: ImageRepository,
    private val saveImage: SaveImageToGalleryUseCase,
) {
    data class Result(val uri: Uri, val width: Int, val height: Int)

    suspend operator fun invoke(
        processed: Bitmap,
        sourceUri: Uri?,
        displayName: String,
        maxPixels: Long,
    ): Result = withContext(Dispatchers.Default) {
        val procPixels = processed.width.toLong() * processed.height.toLong()
        val originalPixels =
            sourceUri?.let { runCatching { repository.readPixelCount(it) }.getOrDefault(0L) } ?: 0L

        // Scale up toward the original size, but never above the safe cap.
        val targetPixels = minOf(originalPixels.coerceAtLeast(procPixels), maxPixels)
        val factor = if (procPixels > 0) sqrt(targetPixels.toDouble() / procPixels) else 1.0

        val output = if (factor > 1.01) {
            processed.scale(
                (processed.width * factor).toInt(),
                (processed.height * factor).toInt(),
            )
        } else {
            processed
        }
        try {
            val uri = saveImage(output, displayName)
            Result(uri, output.width, output.height)
        } finally {
            if (output !== processed) output.recycle()
        }
    }
}
