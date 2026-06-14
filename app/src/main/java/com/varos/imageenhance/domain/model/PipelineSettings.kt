package com.varos.imageenhance.domain.model

/**
 * Immutable, filter-agnostic configuration: a map of filter id -> chosen value.
 *
 * Because it's keyed by id rather than fixed fields, adding a new filter never
 * touches this type. Unset filters fall back to their declared default, so an
 * [EMPTY] settings object means "leave the image untouched".
 */
data class PipelineSettings(val values: Map<String, Float> = emptyMap()) {

    fun valueOf(filter: ImageFilter): Float = values[filter.id] ?: filter.parameter.default

    fun with(filterId: String, value: Float): PipelineSettings =
        copy(values = values + (filterId to value))

    /** True if any registered filter is currently set away from its neutral value. */
    fun isModified(filters: List<ImageFilter>): Boolean =
        filters.any { !it.isNeutral(valueOf(it)) }

    companion object {
        val EMPTY = PipelineSettings()
    }
}
