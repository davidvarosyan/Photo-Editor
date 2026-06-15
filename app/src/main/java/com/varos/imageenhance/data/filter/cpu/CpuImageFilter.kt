package com.varos.imageenhance.data.filter.cpu

import android.graphics.Bitmap
import com.varos.imageenhance.domain.model.ImageFilter

/**
 * CPU backend counterpart of `GlImageFilter`: same domain [ImageFilter] metadata,
 * but renders on the CPU via a bitmap in/out pass instead of a GPU shader.
 *
 * This exists to demonstrate that the pipeline is **backend-agnostic** — the
 * exact same UI, ViewModel and use cases drive either a GPU
 * ([com.varos.imageenhance.data.processor.GpuImageProcessor]) or a CPU
 * ([com.varos.imageenhance.data.processor.CpuImageProcessor]) implementation,
 * selectable in one line in the Koin module. New backends (RenderEffect, native,
 * remote…) can be added the same way.
 */
interface CpuImageFilter : ImageFilter {
    /** Applies the effect at [value], returning a NEW bitmap ([input] untouched). */
    suspend fun apply(input: Bitmap, value: Float): Bitmap
}
