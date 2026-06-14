package com.varos.imageenhance.domain.usecase

import android.graphics.Bitmap
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.processor.ImageProcessor

/** Runs the enhancement pipeline for the given [PipelineSettings]. */
class EnhanceImageUseCase(private val processor: ImageProcessor) {
    suspend operator fun invoke(source: Bitmap, settings: PipelineSettings): Bitmap =
        processor.process(source, settings)
}
