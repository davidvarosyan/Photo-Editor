package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter
import jp.co.cyberagent.android.gpuimage.filter.GPUImage3x3ConvolutionFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBilateralBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import kotlin.math.roundToInt

/** Sharpen, value 0..1 (0 = neutral) → GPUImage sharpness 0..2. */
class SharpenFilter : GlImageFilter {
    override val id = "sharpen"
    override val displayName = "Sharpen"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageSharpenFilter(value * 2f)
}

/**
 * Edge enhancement via a 3x3 Laplacian high-boost convolution: identity plus a
 * scaled edge kernel, so [value] (0..1) blends the effect continuously.
 */
class EdgeEnhanceFilter : GlImageFilter {
    override val id = "edge"
    override val displayName = "Edges"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter {
        val v = value
        return GPUImage3x3ConvolutionFilter().apply {
            setConvolutionKernel(
                floatArrayOf(
                    0f, -v, 0f,
                    -v, 1f + 4f * v, -v,
                    0f, -v, 0f,
                ),
            )
        }
    }
}

/** Gaussian blur, value 0..1 (0 = neutral) → blur size 0..4. */
class BlurFilter : GlImageFilter {
    override val id = "blur"
    override val displayName = "Blur"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageGaussianBlurFilter(value * 4f)
}

/**
 * Noise reduction via an edge-preserving bilateral blur. A lower distance
 * normalization factor means stronger smoothing, so [value] (0..1) maps
 * inversely onto it.
 */
class DenoiseFilter : GlImageFilter {
    override val id = "denoise"
    override val displayName = "Denoise"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter =
        GPUImageBilateralBlurFilter().apply {
            // 8 (light) … ~1.5 (strong) as value goes 0 → 1.
            setDistanceNormalizationFactor(8f - value * 6.5f)
        }
}
