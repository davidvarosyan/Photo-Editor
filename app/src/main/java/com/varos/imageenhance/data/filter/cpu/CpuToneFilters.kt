package com.varos.imageenhance.data.filter.cpu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import kotlin.math.roundToInt

/** Base for CPU filters expressible as a [ColorMatrix] (hardware-accelerated draw). */
abstract class CpuColorMatrixFilter : CpuImageFilter {
    protected abstract fun matrixFor(value: Float): ColorMatrix

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val out = createBitmap(input.width, input.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrixFor(value))
        }
        Canvas(out).drawBitmap(input, 0f, 0f, paint)
        return out
    }
}

class CpuBrightnessFilter : CpuColorMatrixFilter() {
    override val id = "brightness"
    override val displayName = "Brightness"
    override val parameter = FilterParameter(min = -0.5f, max = 0.5f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 200).roundToInt().toString()
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

class CpuContrastFilter : CpuColorMatrixFilter() {
    override val id = "contrast"
    override val displayName = "Contrast"
    override val parameter = FilterParameter(min = 0.6f, max = 1.8f, default = 1f, neutral = 1f)
    override fun format(value: Float) = String.format("%.2f", value)
    override fun matrixFor(value: Float): ColorMatrix {
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

class CpuGrayscaleFilter : CpuColorMatrixFilter() {
    override val id = "grayscale"
    override val displayName = "Grayscale"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )
    override fun matrixFor(value: Float): ColorMatrix = ColorMatrix().apply { setSaturation(0f) }
}
