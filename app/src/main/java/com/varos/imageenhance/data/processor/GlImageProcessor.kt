package com.varos.imageenhance.data.processor

import android.graphics.Bitmap
import com.varos.imageenhance.data.filter.gpu.GlImageFilter
import com.varos.imageenhance.data.processor.gl.GlImageRenderer
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.processor.ImageProcessor
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * GPU [ImageProcessor] backed by our own OpenGL ES renderer (no third-party GL
 * library). Builds the list of active fragment-shader passes and runs them
 * through [GlImageRenderer], which keeps a persistent EGL context and ping-pong
 * FBOs and reads the result back to a bitmap.
 *
 * All GL work is pinned to one dedicated thread (EGL contexts are thread-bound).
 * Shaders are compiled once and cached; the context lives for the renderer's
 * lifetime and is reused across renders and image sizes.
 */
class GlImageProcessor(
    private val filters: List<GlImageFilter>,
) : ImageProcessor {

    private val glDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "gl-render").apply { isDaemon = true } }
            .asCoroutineDispatcher()

    private val renderer = GlImageRenderer()

    override suspend fun process(source: Bitmap, settings: PipelineSettings): Bitmap =
        withContext(glDispatcher) {
            val passes = filters
                .filter { !it.isNeutral(settings.valueOf(it)) }
                .map { it.fragmentShader to settings.valueOf(it) }
            if (passes.isEmpty()) source else renderer.render(source, passes)
        }
}
