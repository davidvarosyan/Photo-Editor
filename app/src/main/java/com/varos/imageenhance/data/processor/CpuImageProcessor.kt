package com.varos.imageenhance.data.processor

import android.graphics.Bitmap
import com.varos.imageenhance.data.filter.cpu.CpuImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.processor.ImageProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * CPU [ImageProcessor] — the parallel backend to [GpuImageProcessor]. Chains the
 * enabled [CpuImageFilter]s on [Dispatchers.Default], recycling intermediates;
 * the original [source] is never mutated. Demonstrates that the same
 * domain/UI/use-case stack works unchanged over a completely different rendering
 * implementation (just swap the binding in the Koin module).
 */
class CpuImageProcessor(
    private val filters: List<CpuImageFilter>,
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
                if (current !== source) current.recycle()
                current = next
            }
            current
        }
}
