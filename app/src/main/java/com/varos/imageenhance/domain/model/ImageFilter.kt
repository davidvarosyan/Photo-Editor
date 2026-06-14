package com.varos.imageenhance.domain.model

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A single, self-contained image-processing step ("functional").
 *
 * This is the core extension point of the app: to add a new capability you
 * implement [apply] and register the class in the Koin module — the pipeline,
 * the tab UI, and the seek bar are all driven generically off this interface, so
 * nothing else has to change.
 *
 * Implementations must be pure (never mutate [input]) and safe to call off the
 * main thread.
 */
interface ImageFilter {
    /** Stable key used to store this filter's value in [PipelineSettings]. */
    val id: String

    /** Human-readable label shown on the tab. */
    val displayName: String

    /** The one knob this filter exposes. */
    val parameter: FilterParameter

    /** Applies the effect at [value], returning a NEW bitmap. */
    suspend fun apply(input: Bitmap, value: Float): Bitmap

    /** True when [value] makes this filter a no-op (pipeline skips it). */
    fun isNeutral(value: Float): Boolean = abs(value - parameter.neutral) < 1e-3f

    /** How [value] is shown next to the seek bar. Override for custom units. */
    fun format(value: Float): String = value.roundToInt().toString()
}
