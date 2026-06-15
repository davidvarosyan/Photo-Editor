package com.varos.imageenhance.data.filter.cpu

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** CPU 3x3 unsharp-mask sharpening, value 0..1 (0 = neutral). */
class CpuSharpenFilter : CpuImageFilter {
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
                val c = src[i]; val up = src[i - w]; val dn = src[i + w]
                val lf = src[i - 1]; val rt = src[i + 1]
                val r = clamp(center * ((c shr 16) and 0xFF) - a * ((up shr 16) and 0xFF) -
                    a * ((dn shr 16) and 0xFF) - a * ((lf shr 16) and 0xFF) - a * ((rt shr 16) and 0xFF))
                val g = clamp(center * ((c shr 8) and 0xFF) - a * ((up shr 8) and 0xFF) -
                    a * ((dn shr 8) and 0xFF) - a * ((lf shr 8) and 0xFF) - a * ((rt shr 8) and 0xFF))
                val b = clamp(center * (c and 0xFF) - a * (up and 0xFF) -
                    a * (dn and 0xFF) - a * (lf and 0xFF) - a * (rt and 0xFF))
                dst[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        return createBitmap(w, h).apply { setPixels(dst, 0, w, 0, 0, w, h) }
    }

    private fun clamp(v: Float) = when {
        v < 0f -> 0; v > 255f -> 255; else -> v.toInt()
    }
}

/** CPU global luminance threshold (B&W), value 0..1; 0 = neutral (skipped). */
class CpuThresholdFilter : CpuImageFilter {
    override val id = "threshold"
    override val displayName = "B&W"
    override val parameter = FilterParameter(min = 0f, max = 1f, default = 0f, neutral = 0f)
    override fun format(value: Float) = (value * 100).roundToInt().toString()

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val threshold = (value * 255f).toInt()
        val w = input.width
        val h = input.height
        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val p = px[i]
                val lum = (0.299f * ((p shr 16) and 0xFF) +
                    0.587f * ((p shr 8) and 0xFF) +
                    0.114f * (p and 0xFF)).toInt()
                px[i] = if (lum >= threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return createBitmap(w, h).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }
}
