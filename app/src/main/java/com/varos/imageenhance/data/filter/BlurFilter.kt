package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Separable box blur. [value] is the radius in pixels (0 = neutral). Two
 * sliding-window passes (horizontal then vertical) keep it O(pixels) regardless
 * of radius. Useful for softening and as a building block for smoothing.
 */
class BlurFilter : ImageFilter {
    override val id = "blur"
    override val displayName = "Blur"
    override val parameter = FilterParameter(min = 0f, max = 25f, default = 0f, neutral = 0f)

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val r = value.toInt().coerceAtLeast(1)
        val w = input.width
        val h = input.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        input.getPixels(a, 0, w, 0, 0, w, h)

        boxBlurHorizontal(a, b, w, h, r)
        boxBlurVertical(b, a, w, h, r)

        return createBitmap(w, h).apply { setPixels(a, 0, w, 0, 0, w, h) }
    }

    private suspend fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            val row = y * w
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = 0xFF shl 24 or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
                val rem = src[row + (x - r).coerceIn(0, w - 1)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }

    private suspend fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            currentCoroutineContext().ensureActive()
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] =
                    0xFF shl 24 or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val rem = src[(y - r).coerceIn(0, h - 1) * w + x]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
