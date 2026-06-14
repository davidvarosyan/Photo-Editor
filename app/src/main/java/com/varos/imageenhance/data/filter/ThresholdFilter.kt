package com.varos.imageenhance.data.filter

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.varos.imageenhance.domain.model.FilterParameter
import com.varos.imageenhance.domain.model.ImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Black & white binarization. [value] is the luminance cut (0..255); a value of
 * 0 (neutral) leaves the image untouched, higher values turn more pixels white.
 * One knob doubles as the on/off control — no separate toggle needed.
 */
class ThresholdFilter : ImageFilter {
    override val id = "threshold"
    override val displayName = "B&W"
    override val parameter = FilterParameter(min = 0f, max = 255f, default = 0f, neutral = 0f)

    override suspend fun apply(input: Bitmap, value: Float): Bitmap {
        val threshold = value.toInt()
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val p = pixels[i]
                val lum = (0.299f * ((p shr 16) and 0xFF) +
                    0.587f * ((p shr 8) and 0xFF) +
                    0.114f * (p and 0xFF)).toInt()
                pixels[i] = if (lum >= threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return createBitmap(w, h).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }
}
