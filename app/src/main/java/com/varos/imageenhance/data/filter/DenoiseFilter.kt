package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/**
 * Noise reduction via a 3x3 median filter, which removes speckle/salt-and-pepper
 * noise while preserving edges better than a blur. [value] (0..1) blends between
 * the original and the median-filtered result so the effect is adjustable.
 */
class DenoiseFilter : ImageFilter {
    override val id = "denoise"
    override val displayName = "Denoise"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)

    override fun format(value: Float) = (value * 100).roundToInt().toString()

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val w = input.width
        val h = input.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        input.getPixels(src, 0, w, 0, 0, w, h)

        val rCh = IntArray(9)
        val gCh = IntArray(9)
        val bCh = IntArray(9)
        val blend = value.coerceIn(0f, 1f)

        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    dst[i] = src[i]
                    continue
                }
                var k = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val p = src[i + dy * w + dx]
                        rCh[k] = (p shr 16) and 0xFF
                        gCh[k] = (p shr 8) and 0xFF
                        bCh[k] = p and 0xFF
                        k++
                    }
                }
                val mr = median9(rCh)
                val mg = median9(gCh)
                val mb = median9(bCh)
                val p = src[i]
                // Blend median result back toward the original by `blend`.
                val r = lerp((p shr 16) and 0xFF, mr, blend)
                val g = lerp((p shr 8) and 0xFF, mg, blend)
                val b = lerp(p and 0xFF, mb, blend)
                dst[i] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        return createBitmap(w, h).apply { setPixels(dst, 0, w, 0, 0, w, h) }
    }

    private fun lerp(from: Int, to: Int, t: Float): Int = (from + (to - from) * t).roundToInt()

    /** Median of 9 ints via a small insertion sort (fast for fixed tiny arrays). */
    private fun median9(a: IntArray): Int {
        for (i in 1 until 9) {
            val v = a[i]
            var j = i - 1
            while (j >= 0 && a[j] > v) {
                a[j + 1] = a[j]
                j--
            }
            a[j + 1] = v
        }
        return a[4]
    }
}
