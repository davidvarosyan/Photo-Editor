package com.varos.imageenhance.data.filter

import android.graphics.ColorMatrix
import com.varos.imageenhance.domain.model.FilterParameter

/** Multiplicative contrast pivoting around mid-gray, value 0.5..2.5 (1 = neutral). */
class ContrastFilter : ColorMatrixFilter() {
    override val id = "contrast"
    override val displayName = "Contrast"
    override val parameter = FilterParameter(min = 0.5f, max = 2.5f, default = 1f, neutral = 1f)

    override fun format(value: Float) = String.format("%.2f", value)

    override fun matrixFor(value: Float): ColorMatrix {
        // translate keeps mid-gray fixed so the image doesn't drift dark/bright.
        val t = (-0.5f * value + 0.5f) * 255f
        return ColorMatrix(
            floatArrayOf(
                value, 0f, 0f, 0f, t,
                0f, value, 0f, 0f, t,
                0f, 0f, value, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
