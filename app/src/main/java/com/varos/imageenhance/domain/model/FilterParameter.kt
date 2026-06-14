package com.varos.imageenhance.domain.model

/**
 * Describes the single adjustable parameter a filter exposes. The UI renders a
 * control purely from this descriptor (a seek bar for [Kind.SLIDER], a switch
 * for [Kind.TOGGLE]) — so a new filter needs no UI changes, just a parameter.
 *
 * @param neutral the value at which the filter is a no-op; the pipeline skips
 *   filters sitting at their neutral value, and "Reset" returns here.
 */
data class FilterParameter(
    val min: Float,
    val max: Float,
    val default: Float,
    val neutral: Float,
    val kind: Kind = Kind.SLIDER,
) {
    enum class Kind { SLIDER, TOGGLE }
}
