package com.varos.imageenhance.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.varos.imageenhance.domain.repository.ImageRepository

/** Decodes, orientation-corrects and downsamples the image at [Uri] to fit [maxPixels]. */
class LoadImageUseCase(private val repository: ImageRepository) {
    suspend operator fun invoke(uri: Uri, maxPixels: Long): Bitmap =
        repository.loadBitmap(uri, maxPixels)
}
