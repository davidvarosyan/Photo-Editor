package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/**
 * Unsharp-mask sharpening via a 3x3 high-pass kernel. [value] (0..1) scales the
 * response so the seek bar feels continuous. Edge pixels pass through untouched.
 */
class SharpenFilter : ImageFilter {
    override val id = "sharpen"
    override val displayName = "Sharpen"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)

    override fun format(value: Float) = (value * 100).roundToInt().toString()

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val w = input.width
        val h = input.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        input.getPixels(src, 0, w, 0, 0, w, h)

        val a = value
        val center = 1f + 4f * a

        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    dst[i] = src[i]
                    continue
                }
                val c = src[i]
                val up = src[i - w]
                val down = src[i + w]
                val left = src[i - 1]
                val right = src[i + 1]

                val r = clamp(
                    center * ((c shr 16) and 0xFF)
                        - a * ((up shr 16) and 0xFF) - a * ((down shr 16) and 0xFF)
                        - a * ((left shr 16) and 0xFF) - a * ((right shr 16) and 0xFF),
                )
                val g = clamp(
                    center * ((c shr 8) and 0xFF)
                        - a * ((up shr 8) and 0xFF) - a * ((down shr 8) and 0xFF)
                        - a * ((left shr 8) and 0xFF) - a * ((right shr 8) and 0xFF),
                )
                val b = clamp(
                    center * (c and 0xFF)
                        - a * (up and 0xFF) - a * (down and 0xFF)
                        - a * (left and 0xFF) - a * (right and 0xFF),
                )
                dst[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
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
