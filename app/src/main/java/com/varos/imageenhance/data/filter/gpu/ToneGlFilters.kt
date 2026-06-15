package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import kotlin.math.roundToInt

/** Brightness, value -1..1 (0 = neutral) — maps directly to GPUImage. */
class BrightnessFilter : GlImageFilter {
    override val id = "brightness"
    override val displayName = "Brightness"
    override val parameter = FilterParameter(min = -1f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageBrightnessFilter(value)
}

/** Contrast, value 0.5..2.5 (1 = neutral). */
class ContrastFilter : GlImageFilter {
    override val id = "contrast"
    override val displayName = "Contrast"
    override val parameter = FilterParameter(min = 0.5f, max = 2.5f, default = 1f, neutral = 1f)
    override fun format(value: Float) = String.format("%.2f", value)
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageContrastFilter(value)
}

/** Grayscale (toggle). */
class GrayscaleFilter : GlImageFilter {
    override val id = "grayscale"
    override val displayName = "Grayscale"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageGrayscaleFilter()
}
