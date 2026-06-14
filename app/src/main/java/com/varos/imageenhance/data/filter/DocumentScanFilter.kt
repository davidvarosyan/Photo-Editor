package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One-tap document readability: Bradley adaptive thresholding.
 *
 * Each pixel is compared against the mean luminance of its local neighbourhood
 * (computed in O(pixels) from an integral image) rather than a single global
 * cut. This handles uneven lighting and shadows far better than plain
 * binarization — the typical case for receipts, notes and book pages. Rendered
 * as a toggle (0 = off, 1 = on).
 */
class DocumentScanFilter : ImageFilter {
    override val id = "document"
    override val displayName = "Document"
    override val parameter = FilterParameter(
        min = 0f, max = 1f, default = 0f, neutral = 0f, kind = FilterParameter.Kind.TOGGLE,
    )

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)

        // Luminance + integral (summed-area) table. Longs avoid overflow on big images.
        val lum = IntArray(w * h)
        val integral = LongArray(w * h)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            var rowSum = 0L
            for (x in 0 until w) {
                val i = y * w + x
                val p = pixels[i]
                val l = (0.299f * ((p shr 16) and 0xFF) +
                    0.587f * ((p shr 8) and 0xFF) +
                    0.114f * (p and 0xFF)).toInt()
                lum[i] = l
                rowSum += l
                integral[i] = rowSum + if (y > 0) integral[i - w] else 0L
            }
        }

        // Window ~ 1/8 of the width; pixel is white unless it's >=t% darker than
        // its local mean (t = 0.15 is the standard Bradley constant).
        val radius = (w / 16).coerceAtLeast(8)
        val t = 0.15f

        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(w - 1)
                val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
                val sum = areaSum(integral, w, x0, y0, x1, y1)
                val i = y * w + x
                // Bradley: white unless the pixel is >= t darker than the local mean.
                val isWhite = lum[i].toLong() * count > (sum.toDouble() * (1f - t)).toLong()
                pixels[i] = if (isWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return createBitmap(w, h).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    /** Sum of luminance over the inclusive rectangle (x0,y0)..(x1,y1). */
    private fun areaSum(integral: LongArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val d = integral[y1 * w + x1]
        val b = if (x0 > 0) integral[y1 * w + (x0 - 1)] else 0L
        val c = if (y0 > 0) integral[(y0 - 1) * w + x1] else 0L
        val a = if (x0 > 0 && y0 > 0) integral[(y0 - 1) * w + (x0 - 1)] else 0L
        return d - b - c + a
    }
}
