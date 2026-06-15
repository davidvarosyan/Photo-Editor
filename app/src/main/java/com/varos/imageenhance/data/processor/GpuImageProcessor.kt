package com.varos.imageenhance.data.processor

import android.graphics.Bitmap
import com.varos.imageenhance.data.filter.gpu.GlImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.processor.ImageProcessor
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer
import jp.co.cyberagent.android.gpuimage.PixelBuffer
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * GPU [ImageProcessor] that runs the enabled [GlImageFilter]s as one GLES filter
 * group, off-screen, and reads the result back to a bitmap.
 *
 * Performance: a single EGL context ([PixelBuffer]) and [GPUImageRenderer] are
 * **reused across renders** instead of allocating a fresh `GPUImage`/EGL context
 * every call (the dominant per-frame cost). The buffer is only recreated when the
 * image dimensions change (e.g. switching between the editing copy and a full-res
 * export). All GL work is pinned to one dedicated thread so the context stays
 * valid — required by EGL/`PixelBuffer` thread affinity.
 *
 * The input is assumed to already fit a safe GL texture size (the caller bounds
 * it through MemoryPolicy), so no tiling is needed.
 */
class GpuImageProcessor(
    private val filters: List<GlImageFilter>,
) : ImageProcessor {

    // One thread for the whole GL lifetime → the EGL context is always current.
    private val glDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "gpu-render").apply { isDaemon = true } }
            .asCoroutineDispatcher()

    private val renderer = GPUImageRenderer(GPUImageFilter())
    private var pixelBuffer: PixelBuffer? = null
    private var bufferWidth = -1
    private var bufferHeight = -1

    override suspend fun process(source: Bitmap, settings: PipelineSettings): Bitmap =
        withContext(glDispatcher) {
            val active = filters.filter { !it.isNeutral(settings.valueOf(it)) }
            if (active.isEmpty()) return@withContext source

            ensureBuffer(source.width, source.height)
            renderer.setFilter(GPUImageFilterGroup(active.map { it.gpuFilter(settings.valueOf(it)) }))
            renderer.setImageBitmap(source, false) // false = don't recycle the source
            pixelBuffer!!.bitmap
        }

    /** Reuses the EGL context/buffer; only rebuilds it when the size changes. */
    private fun ensureBuffer(width: Int, height: Int) {
        if (pixelBuffer != null && bufferWidth == width && bufferHeight == height) return
        pixelBuffer?.destroy()
        pixelBuffer = PixelBuffer(width, height).also { it.setRenderer(renderer) }
        bufferWidth = width
        bufferHeight = height
    }
}
