package com.varos.imageenhance.data.filter

import android.graphics.ColorMatrix
import com.varos.imageenhance.domain.model.FilterParameter
import kotlin.math.roundToInt

/** Additive brightness, value in -1..1 (0 = neutral). */
class BrightnessFilter : ColorMatrixFilter() {
    override val id = "brightness"
    override val displayName = "Brightness"
    override val parameter = FilterParameter(min = -1f, max = 1f, default = 0f, neutral = 0f)

    override fun format(value: Float) = (value * 100).roundToInt().toString()

    override fun matrixFor(value: Float): ColorMatrix {
        val b = value * 255f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
