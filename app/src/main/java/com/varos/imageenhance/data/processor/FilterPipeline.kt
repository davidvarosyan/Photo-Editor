package com.varos.imageenhance.data.processor

import android.graphics.Bitmap
import com.varos.imageenhance.domain.processor.ImageProcessor
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * [ImageProcessor] that chains the injected [filters] in order. Each registered
 * filter is applied only if its current value is non-neutral, so the pipeline is
 * fully data-driven — adding a filter to the Koin module is all it takes for it
 * to participate here, in the chosen order.
 *
 * Intermediate bitmaps are recycled as we go to keep peak memory low; the
 * original [source] is never recycled or mutated.
 */
class FilterPipeline(
    private val filters: List<ImageFilter>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ImageProcessor {

    override suspend fun process(source: Bitmap, settings: PipelineSettings): Bitmap =
        withContext(dispatcher) {
            var current = source
            for (filter in filters) {
                currentCoroutineContext().ensureActive()
                val value = settings.valueOf(filter)
                if (filter.isNeutral(value)) continue

                val next = filter.apply(current, value)
                if (current !== source) current.recycle() // free the prior intermediate
                current = next
            }
            current
        }
}
