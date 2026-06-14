package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Edge enhancement via the Sobel gradient magnitude added back onto the image
 * (high-boost). Unlike unsharp sharpening, this specifically emphasizes outlines
 * — helpful for making text and line art "pop". [value] (0..1) is the strength.
 */
class EdgeEnhanceFilter : ImageFilter {
    override val id = "edge"
    override val displayName = "Edges"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)

    override fun format(value: Float) = (value * 100).roundToInt().toString()

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val w = input.width
        val h = input.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        input.getPixels(src, 0, w, 0, 0, w, h)

        // Precompute luminance for gradient computation.
        val lum = IntArray(w * h)
        for (i in src.indices) {
            val p = src[i]
            lum[i] = (0.299f * ((p shr 16) and 0xFF) +
                0.587f * ((p shr 8) and 0xFF) +
                0.114f * (p and 0xFF)).toInt()
        }

        val amount = value
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    dst[i] = src[i]
                    continue
                }
                // Sobel Gx / Gy on luminance.
                val tl = lum[i - w - 1]; val tc = lum[i - w]; val tr = lum[i - w + 1]
                val ml = lum[i - 1]; val mr = lum[i + 1]
                val bl = lum[i + w - 1]; val bc = lum[i + w]; val br = lum[i + w + 1]
                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                val edge = (abs(gx) + abs(gy)) * amount

                val p = src[i]
                val r = clamp(((p shr 16) and 0xFF) + edge)
                val g = clamp(((p shr 8) and 0xFF) + edge)
                val b = clamp((p and 0xFF) + edge)
                dst[i] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        return createBitmap(w, h).apply { setPixels(dst, 0, w, 0, 0, w, h) }
    }

    private fun clamp(v: Float): Int = when {
        v < 0f -> 0
        v > 255f -> 255
        else -> v.toInt()
    }
}
