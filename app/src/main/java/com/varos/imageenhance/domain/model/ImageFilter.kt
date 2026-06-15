package com.varos.imageenhance.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Metadata describing one image-processing functional ("tool"). Pure domain —
 * no Android/graphics types — so the UI (tabs, seek bars) and the settings model
 * depend only on this. The actual rendering is supplied in the data layer (see
 * `GlImageFilter`, which adds a GPU factory).
 *
 * Adding a functional = implement this (via `GlImageFilter`) and register it in
 * the Koin module; everything else is generic over the interface.
 */
interface ImageFilter {
    /** Stable key used to store this filter's value in [PipelineSettings]. */
    val id: String

    /** Human-readable label shown on the tab. */
    val displayName: String

    /** The one knob this filter exposes. */
    val parameter: FilterParameter

    /** True when [value] makes this filter a no-op (the pipeline skips it). */
    fun isNeutral(value: Float): Boolean = abs(value - parameter.neutral) < 1e-3f

    /** How [value] is shown next to the seek bar. Override for custom units. */
    fun format(value: Float): String = value.roundToInt().toString()
}
