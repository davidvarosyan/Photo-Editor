package com.varos.imageenhance.di

import com.varos.imageenhance.data.AndroidImageRepository
import com.varos.imageenhance.data.FilterPipeline
import com.varos.imageenhance.data.filter.BlurFilter
import com.varos.imageenhance.data.filter.BrightnessFilter
import com.varos.imageenhance.data.filter.ContrastFilter
import com.varos.imageenhance.data.filter.DenoiseFilter
import com.varos.imageenhance.data.filter.DocumentScanFilter
import com.varos.imageenhance.data.filter.EdgeEnhanceFilter
import com.varos.imageenhance.data.filter.GrayscaleFilter
import com.varos.imageenhance.data.filter.SharpenFilter
import com.varos.imageenhance.data.filter.ThresholdFilter
import com.varos.imageenhance.domain.ImageProcessor
import com.varos.imageenhance.domain.ImageRepository
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.usecase.CreateCaptureUriUseCase
import com.varos.imageenhance.domain.usecase.EnhanceImageUseCase
import com.varos.imageenhance.domain.usecase.GetFiltersUseCase
import com.varos.imageenhance.domain.usecase.LoadImageUseCase
import com.varos.imageenhance.domain.usecase.SaveImageUseCase
import com.varos.imageenhance.ui.editor.EditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Single composition root. Everything is wired here, so dependencies flow one
 * way and each layer stays unaware of how its collaborators are built.
 *
 * === Adding a new image-processing functional ===
 * 1. Implement [ImageFilter] (typically extending ColorMatrixFilter or writing a
 *    pixel pass like SharpenFilter).
 * 2. Add it to the `filters` list below, in the position you want it to run.
 * That's it — it automatically appears as a tab with its seek bar and joins the
 * pipeline. No UI, ViewModel, or state changes are required.
 */
val appModule = module {

    // ----- The registry of image-processing functionals (order = pipeline order
    // = tab order). This list is the one place you touch to add a new filter. ---
    single<List<ImageFilter>> {
        listOf(
            BrightnessFilter(),
            ContrastFilter(),
            DenoiseFilter(),
            SharpenFilter(),
            EdgeEnhanceFilter(),
            BlurFilter(),
            GrayscaleFilter(),
            ThresholdFilter(),
            DocumentScanFilter(),
        )
    }

    // ----- Data layer -----
    single<ImageRepository> { AndroidImageRepository(androidContext()) }
    single<ImageProcessor> { FilterPipeline(filters = get()) }

    // ----- Use cases -----
    factory { LoadImageUseCase(get()) }
    factory { EnhanceImageUseCase(get()) }
    factory { SaveImageUseCase(get()) }
    factory { CreateCaptureUriUseCase(get()) }
    factory { GetFiltersUseCase(get()) }

    // ----- Presentation -----
    viewModelOf(::EditorViewModel)
}
