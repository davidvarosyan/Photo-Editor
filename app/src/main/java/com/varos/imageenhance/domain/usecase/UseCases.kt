package com.varos.imageenhance.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.varos.imageenhance.domain.ImageProcessor
import com.varos.imageenhance.domain.ImageRepository
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings

/**
 * Application use cases — one intent each, expressed as callable objects.
 *
 * They keep the ViewModel free of any knowledge about *how* an operation is
 * carried out (which repository, which processor), and give a single, testable
 * seam per user action. They're deliberately thin: the value is in the explicit
 * boundary, not in added logic.
 */

/** Decodes, orientation-corrects and downsamples the image at [uri]. */
class LoadImageUseCase(private val repository: ImageRepository) {
    suspend operator fun invoke(uri: Uri, maxEdge: Int = 2048): Bitmap =
        repository.loadBitmap(uri, maxEdge)
}

/** Runs the enhancement pipeline for the given [settings]. */
class EnhanceImageUseCase(private val processor: ImageProcessor) {
    suspend operator fun invoke(source: Bitmap, settings: PipelineSettings): Bitmap =
        processor.process(source, settings)
}

/** Persists the enhanced [bitmap] to the device gallery. */
class SaveImageUseCase(private val repository: ImageRepository) {
    suspend operator fun invoke(bitmap: Bitmap, displayName: String): Uri =
        repository.saveToGallery(bitmap, displayName)
}

/** Provides a writable Uri for the camera to capture into. */
class CreateCaptureUriUseCase(private val repository: ImageRepository) {
    operator fun invoke(): Uri = repository.createCaptureUri()
}

/** Exposes the registered filters so the UI can build its tabs/controls. */
class GetFiltersUseCase(private val filters: List<ImageFilter>) {
    operator fun invoke(): List<ImageFilter> = filters
}
