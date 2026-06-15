package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.ImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * A GPU-backed filter: domain [ImageFilter] metadata plus a factory that builds
 * the configured GPUImage fragment-shader filter for a given value.
 *
 * Keeping the GPU type (GPUImageFilter) here in the data layer keeps the domain
 * pure. The processor ([com.varos.imageenhance.data.processor.GpuImageProcessor])
 * composes these into a single GLES filter group and renders them off-screen.
 */
interface GlImageFilter : ImageFilter {
    /** Builds the GLES filter for [value]; only called when the value is non-neutral. */
    fun gpuFilter(value: Float): GPUImageFilter
}
