package com.varos.imageenhance.data.filter

import android.graphics.ColorMatrix
import com.varos.imageenhance.domain.model.FilterParameter

/** Desaturate to grayscale. Rendered as a toggle (0 = off, 1 = on). */
class GrayscaleFilter : ColorMatrixFilter() {
    override val id = "grayscale"
    override val displayName = "Grayscale"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )

    override fun matrixFor(value: Float): ColorMatrix = ColorMatrix().apply { setSaturation(0f) }
}
