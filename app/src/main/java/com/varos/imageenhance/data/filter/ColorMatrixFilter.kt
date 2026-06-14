package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.ImageFilter

/**
 * Base class for filters expressible as a [ColorMatrix] (tone, grayscale, etc.).
 * Subclasses only supply the matrix for a given value; the (fast, hardware)
 * bitmap pass is shared here.
 */
abstract class ColorMatrixFilter : ImageFilter {

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
