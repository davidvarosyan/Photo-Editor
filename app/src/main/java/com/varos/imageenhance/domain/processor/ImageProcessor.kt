package com.varos.imageenhance.domain.processor

import android.graphics.Bitmap
import com.varos.imageenhance.domain.model.PipelineSettings

/**
 * Runs the configured chain of [com.varos.imageenhance.domain.model.ImageFilter]
 * steps. The domain depends on this interface, not on any Android graphics
 * implementation, so the pipeline can be swapped without touching the use cases
 * or ViewModel.
 */
interface ImageProcessor {
    /**
     * Applies every non-neutral filter named in [settings] to [source], in the
     * registered order, and returns a new bitmap. [source] is never mutated.
     * Must be safe to call off the main thread and cooperatively cancellable.
     */
    suspend fun process(source: Bitmap, settings: PipelineSettings): Bitmap
}
