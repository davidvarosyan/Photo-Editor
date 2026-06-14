package com.varos.imageenhance.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.varos.imageenhance.domain.repository.ImageRepository

/** Decodes, orientation-corrects and downsamples the image at [Uri]. */
class LoadImageUseCase(private val repository: ImageRepository) {
    suspend operator fun invoke(uri: Uri, maxEdge: Int = 2048): Bitmap =
        repository.loadBitmap(uri, maxEdge)
}
