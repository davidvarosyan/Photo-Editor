package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import kotlin.math.roundToInt

/**
 * Brightness. GPUImage's usable range is -1..1 where ±1 is solid black/white, so
 * we cap it at ±0.5 (a meaningful adjustment) and display it as a natural
 * -100..100 to the user.
 */
class BrightnessFilter : GlImageFilter {
    override val id = "brightness"
    override val displayName = "Brightness"
    override val parameter = FilterParameter(min = -0.5f, max = 0.5f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 200).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageBrightnessFilter(value)
}

/** Contrast, kept to a tasteful 0.6..1.8 (1 = neutral). */
class ContrastFilter : GlImageFilter {
    override val id = "contrast"
    override val displayName = "Contrast"
    override val parameter = FilterParameter(min = 0.6f, max = 1.8f, default = 1f, neutral = 1f)
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
