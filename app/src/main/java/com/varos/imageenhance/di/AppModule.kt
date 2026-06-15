package com.varos.imageenhance.di

import com.varos.imageenhance.data.filter.gpu.BlurFilter
import com.varos.imageenhance.data.filter.gpu.BrightnessFilter
import com.varos.imageenhance.data.filter.gpu.ContrastFilter
import com.varos.imageenhance.data.filter.gpu.DenoiseFilter
import com.varos.imageenhance.data.filter.gpu.DocumentScanFilter
import com.varos.imageenhance.data.filter.gpu.EdgeEnhanceFilter
import com.varos.imageenhance.data.filter.gpu.GlImageFilter
import com.varos.imageenhance.data.filter.gpu.GrayscaleFilter
import com.varos.imageenhance.data.filter.gpu.SharpenFilter
import com.varos.imageenhance.data.filter.cpu.CpuBrightnessFilter
import com.varos.imageenhance.data.filter.cpu.CpuContrastFilter
import com.varos.imageenhance.data.filter.cpu.CpuGrayscaleFilter
import com.varos.imageenhance.data.filter.cpu.CpuImageFilter
import com.varos.imageenhance.data.filter.cpu.CpuSharpenFilter
import com.varos.imageenhance.data.processor.CpuImageProcessor
import com.varos.imageenhance.data.processor.GpuImageProcessor
import com.varos.imageenhance.data.repository.AndroidImageRepository
import com.varos.imageenhance.domain.policy.MemoryPolicy
import com.varos.imageenhance.domain.processor.ImageProcessor
import com.varos.imageenhance.domain.repository.ImageRepository
import com.varos.imageenhance.domain.usecase.CreateCaptureUriUseCase
import com.varos.imageenhance.domain.usecase.EnhanceImageUseCase
import com.varos.imageenhance.domain.usecase.GetFiltersUseCase
import com.varos.imageenhance.domain.usecase.LoadImageUseCase
import com.varos.imageenhance.domain.usecase.SaveEditedImageUseCase
import com.varos.imageenhance.domain.usecase.SaveImageToGalleryUseCase
import com.varos.imageenhance.presentation.editor.EditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Pluggable rendering backend. Flip this to swap the *entire* image-processing
 * implementation — the UI, ViewModel and use cases are unaware which runs.
 *
 *  - `true`  → [GpuImageProcessor] (OpenGL ES via GPUImage), the full filter set.
 *  - `false` → [CpuImageProcessor] (bitmap passes), a parallel CPU backend kept
 *              in the codebase to prove the architecture is backend-agnostic and
 *              extensible (a subset of filters, easily expanded).
 */
private const val USE_GPU = true

/**
 * Single composition root. Dependencies flow one way; each layer stays unaware
 * of how its collaborators are built.
 *
 * === Adding a new functional ===
 * Implement `GlImageFilter` (GPU) and/or `CpuImageFilter` (CPU), then add it to
 * the matching `filters` list below. It automatically becomes a tab with its
 * seek bar and joins the pipeline — no UI/ViewModel/state changes.
 *
 * NOTE: only ONE `List<…Filter>` is registered at a time. Koin keys generics by
 * erased KClass (`List`), so registering two list types would collide; the
 * active backend's list also serves `GetFiltersUseCase`'s `List<ImageFilter>`
 * (both `GlImageFilter` and `CpuImageFilter` are `ImageFilter`).
 */
val appModule = module {

    // ----- Rendering backend (filters + processor), selected by USE_GPU --------
    if (USE_GPU) {
        single<List<GlImageFilter>> {
            listOf(
                BrightnessFilter(),
                ContrastFilter(),
                DenoiseFilter(),
                SharpenFilter(),
                EdgeEnhanceFilter(),
                BlurFilter(),
                GrayscaleFilter(),
                DocumentScanFilter(),
            )
        }
        single<ImageProcessor> { GpuImageProcessor(filters = get()) }
    } else {
        single<List<CpuImageFilter>> {
            listOf(
                CpuBrightnessFilter(),
                CpuContrastFilter(),
                CpuSharpenFilter(),
                CpuGrayscaleFilter(),
            )
        }
        single<ImageProcessor> { CpuImageProcessor(filters = get()) }
    }

    // ----- Data & policy -----
    single<ImageRepository> { AndroidImageRepository(androidContext()) }
    single { MemoryPolicy() }

    // ----- Use cases -----
    factory { LoadImageUseCase(get()) }
    factory { EnhanceImageUseCase(get()) }
    factory { SaveImageToGalleryUseCase(get()) }
    factory { SaveEditedImageUseCase(get(), get()) }
    factory { CreateCaptureUriUseCase(get()) }
    factory { GetFiltersUseCase(get()) }

    // ----- Presentation (SavedStateHandle is supplied by Koin) -----
    viewModelOf(::EditorViewModel)
}
