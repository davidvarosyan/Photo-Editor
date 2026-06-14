package com.varos.imageenhance.domain.usecase

import com.varos.imageenhance.domain.model.ImageFilter

/** Exposes the registered filters so the UI can build its tabs/controls. */
class GetFiltersUseCase(private val filters: List<ImageFilter>) {
    operator fun invoke(): List<ImageFilter> = filters
}
