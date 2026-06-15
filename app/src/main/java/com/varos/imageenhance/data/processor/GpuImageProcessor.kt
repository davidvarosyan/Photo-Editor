package com.varos.imageenhance.data.processor

import android.content.Context
import android.graphics.Bitmap
import com.varos.imageenhance.data.filter.GlImageFilter
import com.varos.imageenhance.domain.processor.ImageProcessor
import com.varos.imageenhance.domain.model.PipelineSettings
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ImageProcessor] that runs the enabled [GlImageFilter]s as a single GLES
 * filter group, rendered **off-screen on the GPU** via GPUImage and read back
 * to a bitmap. Every enabled filter runs in one upload → multi-pass → readback,
 * which is dramatically faster than per-pixel CPU passes for convolutions.
 *
 * The input is assumed to already fit within a safe GL texture size (the caller
 * bounds it through MemoryPolicy), so no tiling is needed here.
 */
class GpuImageProcessor(
    private val context: Context,
    private val filters: List<GlImageFilter>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ImageProcessor {

    override suspend fun process(source: Bitmap, settings: PipelineSettings): Bitmap =
        withContext(dispatcher) {
            val active = filters.filter { !it.isNeutral(settings.valueOf(it)) }
            if (active.isEmpty()) return@withContext source

            val gpuImage = GPUImage(context)
            gpuImage.setImage(source)
            gpuImage.setFilter(GPUImageFilterGroup(active.map { it.gpuFilter(settings.valueOf(it)) }))
            gpuImage.bitmapWithFilterApplied
        }
}
