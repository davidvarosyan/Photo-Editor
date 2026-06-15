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
 *  - [editingMaxPixels]: the on-screen working copy. Smaller, because the source,
 *    a preview copy, the processed result and the pipeline peak are all alive at
 *    once during editing.
 *  - [exportMaxPixels]: a one-shot full-resolution render at save time. Larger,
 *    because it's transient. Images that already fit are saved at their TRUE
 *    original resolution; only bigger-than-budget images (e.g. gigapixel maps)
 *    are downsampled to the largest size that fits.
 *
 * Divisors are deliberately conservative; tune with care.
 */
class MemoryPolicy(
    private val maxHeapBytes: Long = Runtime.getRuntime().maxMemory(),
) {
    fun editingMaxPixels(): Long = (maxHeapBytes / EDITING_DIVISOR).coerceAtMost(MAX_GPU_PIXELS)
    fun exportMaxPixels(): Long = (maxHeapBytes / EXPORT_DIVISOR).coerceAtMost(MAX_GPU_PIXELS)

    private companion object {
        // heap / 48 ≈ working bitmap uses ~1/12 of heap after the ~4x pipeline peak.
        const val EDITING_DIVISOR = 48L
        // heap / 28 ≈ export bitmap + its transient ~4x peak stay within heap.
        const val EXPORT_DIVISOR = 28L

        // GPU rendering needs the bitmap to fit one texture. 4096² is the safe
        // floor virtually all API-28+ GPUs support; bigger images are downsampled.
        const val MAX_GPU_PIXELS = 4096L * 4096L
    }
}
