package com.varos.imageenhance.data.filter

import com.varos.imageenhance.domain.model.FilterParameter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLuminanceThresholdFilter
import kotlin.math.roundToInt

/**
 * Global black & white threshold. value 0..1 is the luminance cut; 0 (neutral)
 * leaves the image untouched (skipped), higher turns more pixels white — so the
 * single knob doubles as on/off.
 */
class ThresholdFilter : GlImageFilter {
    override val id = "threshold"
    override val displayName = "B&W"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()
    override fun gpuFilter(value: Float): GPUImageFilter = GPUImageLuminanceThresholdFilter(value)
}

/**
 * One-tap document readability: adaptive thresholding (each pixel vs its local
 * neighbourhood mean), which handles uneven lighting/shadows on receipts, notes
 * and pages far better than a global cut. Rendered as a toggle.
 */
class DocumentScanFilter : GlImageFilter {
    override val id = "document"
    override val displayName = "Document"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )
    override fun gpuFilter(value: Float): GPUImageFilter = GpuAdaptiveThresholdFilter()
}
