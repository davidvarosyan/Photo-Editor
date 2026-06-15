package com.varos.imageenhance.domain.policy

/**
 * Derives safe working/export image sizes from the available heap, so the app
 * uses as much resolution as it can without risking OutOfMemory.
 *
 * Why pixel budgets (not a fixed edge): an ARGB_8888 bitmap costs 4 bytes/px,
 * and the CPU pipeline allocates several transient buffers on top (a convolution
 * pass holds the input bitmap + two int arrays + the output ≈ 4x). The budgets
 * below leave room for that peak plus the live UI.
 *
 * [editingMaxPixels] is the size of the on-screen working copy. It **scales with
 * the device**: derived from the heap (a good proxy for overall capability —
 * more RAM usually means a stronger GPU), then clamped between a low-end floor
 * (stay smooth) and a high-end ceiling (don't render needlessly large). So a
 * budget phone edits at ~[EDITING_MIN_PIXELS] while a flagship goes much higher.
 *
 * [saveMaxPixels] bounds the saved file: editing happens small, but on save the
 * previewed result is upscaled back to the *original* image resolution (so the
 * saved file keeps the source's dimensions), capped here so an enormous original
 * can't OOM the upscale.
 *
 * Editing never exceeds one GL texture.
 */
class MemoryPolicy(
    private val maxHeapBytes: Long = Runtime.getRuntime().maxMemory(),
) {
    fun editingMaxPixels(): Long =
        (maxHeapBytes / EDITING_DIVISOR)
            .coerceIn(EDITING_MIN_PIXELS, EDITING_MAX_PIXELS)
            .coerceAtMost(MAX_GPU_PIXELS)

    fun saveMaxPixels(): Long = minOf(maxHeapBytes / SAVE_DIVISOR, SAVE_CAP_PIXELS)

    private companion object {
        // Working copy ≈ heap / 64, e.g. 256MB→4MP, 512MB→8MP, 1GB→~12MP (clamped).
        const val EDITING_DIVISOR = 64L
        const val EDITING_MIN_PIXELS = 2_500_000L  // low-end floor — keep it smooth
        const val EDITING_MAX_PIXELS = 12_000_000L // high-end ceiling

        // Upper bound for the upscaled saved bitmap (heap-guarded, hard cap 24 MP)
        // so a gigapixel original is saved at the largest safe size, not its full.
        const val SAVE_DIVISOR = 20L
        const val SAVE_CAP_PIXELS = 24_000_000L

        // GPU rendering needs the bitmap to fit one texture.
        const val MAX_GPU_PIXELS = 4096L * 4096L
    }
}
