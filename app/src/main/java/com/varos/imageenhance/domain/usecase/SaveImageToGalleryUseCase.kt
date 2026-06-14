package com.varos.imageenhance.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.varos.imageenhance.domain.repository.ImageRepository

/** Persists the enhanced [bitmap] to the device gallery. */
class SaveImageToGalleryUseCase(private val repository: ImageRepository) {
    suspend operator fun invoke(bitmap: Bitmap, displayName: String): Uri =
        repository.saveToGallery(bitmap, displayName)
}
