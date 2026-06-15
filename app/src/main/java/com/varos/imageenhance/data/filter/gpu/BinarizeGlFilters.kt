package com.varos.imageenhance.data.filter.gpu

import com.varos.imageenhance.domain.model.FilterParameter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * One-tap document readability: adaptive thresholding (each pixel vs its local
 * neighbourhood mean), which handles uneven lighting/shadows on receipts, notes
 * and pages far better than a global cut. Rendered as a toggle.
 *
 * (A plain global luminance threshold was removed — it flips the whole image to
 * black/white abruptly and gives a poor slider experience; adaptive document
 * thresholding is the meaningful binarization here.)
 */
class DocumentScanFilter : GlImageFilter {
    override val id = "document"
    override val displayName = "Document"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )
    override fun gpuFilter(value: Float): GPUImageFilter = GpuAdaptiveThresholdFilter()
}
