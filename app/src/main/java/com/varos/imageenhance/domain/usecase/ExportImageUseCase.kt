package com.varos.imageenhance.domain.usecase

import android.net.Uri
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.policy.MemoryPolicy

/**
 * Renders and saves the image at the **highest resolution that fits safely**.
 *
 * Editing happens on a small working copy, but saving re-decodes the *original*
 * at [MemoryPolicy.exportMaxPixels] and re-runs the pipeline on it, so the output
 * is full original resolution whenever it fits the heap budget. Only images
 * larger than the budget (e.g. gigapixel maps) are downsampled — never enough to
 * risk OutOfMemory. The processed bitmap is recycled before returning.
 */
class ExportImageUseCase(
    private val loadImage: LoadImageUseCase,
    private val enhanceImage: EnhanceImageUseCase,
    private val saveImage: SaveImageToGalleryUseCase,
    private val memoryPolicy: MemoryPolicy,
) {
    data class Result(val savedUri: Uri, val width: Int, val height: Int)

    suspend operator fun invoke(
        uri: Uri,
        settings: PipelineSettings,
        displayName: String,
    ): Result {
        val full = loadImage(uri, memoryPolicy.exportMaxPixels())
        return try {
            val output = enhanceImage(full, settings)
            try {
                val saved = saveImage(output, displayName)
                Result(saved, output.width, output.height)
            } finally {
                if (output !== full) output.recycle()
            }
        } finally {
            full.recycle()
        }
    }
}
