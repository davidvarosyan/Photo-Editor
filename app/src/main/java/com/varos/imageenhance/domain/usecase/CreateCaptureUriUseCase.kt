package com.varos.imageenhance.domain.usecase

import android.net.Uri
import com.varos.imageenhance.domain.repository.ImageRepository

/** Provides a writable Uri for the camera to capture into. */
class CreateCaptureUriUseCase(private val repository: ImageRepository) {
    operator fun invoke(): Uri = repository.createCaptureUri()
}
