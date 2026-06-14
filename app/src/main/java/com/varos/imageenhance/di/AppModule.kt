package com.varos.imageenhance.di

import com.varos.imageenhance.data.filter.BrightnessFilter
import com.varos.imageenhance.data.filter.ContrastFilter
import com.varos.imageenhance.data.filter.GrayscaleFilter
import com.varos.imageenhance.data.filter.SharpenFilter
import com.varos.imageenhance.data.filter.ThresholdFilter
import com.varos.imageenhance.data.processor.FilterPipeline
import com.varos.imageenhance.data.repository.AndroidImageRepository
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.processor.ImageProcessor
import com.varos.imageenhance.domain.repository.ImageRepository
import com.varos.imageenhance.domain.usecase.CreateCaptureUriUseCase
import com.varos.imageenhance.domain.usecase.EnhanceImageUseCase
import com.varos.imageenhance.domain.usecase.GetFiltersUseCase
import com.varos.imageenhance.domain.usecase.LoadImageUseCase
import com.varos.imageenhance.domain.usecase.SaveImageToGalleryUseCase
import com.varos.imageenhance.presentation.editor.EditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Single composition root. Dependencies flow one way; each layer stays unaware
 * of how its collaborators are built.
 *
 * === Adding a new image-processing functional ===
 * 1. Implement [ImageFilter] (e.g. extend ColorMatrixFilter, or write a pixel
 *    pass like SharpenFilter).
 * 2. Add it to the `filters` list below, in the position you want it to run.
 * It then automatically appears as a tab with its seek bar and joins the
 * pipeline. No UI, ViewModel, or state changes required.
 */
val appModule = module {

    // ----- The registry of functionals (order = pipeline order = tab order).
    // The one place to touch when adding a filter. Kept light and simple. -------
    single<List<ImageFilter>> {
        listOf(
            BrightnessFilter(),
            ContrastFilter(),
            SharpenFilter(),
            GrayscaleFilter(),
            ThresholdFilter(),
        )
    }

    // ----- Data -----
    single<ImageRepository> { AndroidImageRepository(androidContext()) }
    single<ImageProcessor> { FilterPipeline(filters = get()) }

    // ----- Use cases -----
    factory { LoadImageUseCase(get()) }
    factory { EnhanceImageUseCase(get()) }
    factory { SaveImageToGalleryUseCase(get()) }
    factory { CreateCaptureUriUseCase(get()) }
    factory { GetFiltersUseCase(get()) }

    // ----- Presentation (SavedStateHandle is supplied by Koin) -----
    viewModelOf(::EditorViewModel)
}
