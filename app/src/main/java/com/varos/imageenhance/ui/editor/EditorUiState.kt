package com.varos.imageenhance.ui.editor

import android.graphics.Bitmap
import com.varos.imageenhance.domain.model.PipelineSettings

/**
 * Single immutable snapshot of everything the editor screen renders. The
 * ViewModel exposes a StateFlow of this type; Compose re-renders from it. Having
 * one state object (rather than scattered fields) keeps the UI a pure function
 * of state and makes the data flow easy to reason about.
 */
data class EditorUiState(
    val original: Bitmap? = null,
    val processed: Bitmap? = null,
    val settings: PipelineSettings = PipelineSettings.EMPTY,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    /** One-shot user-facing message (errors, save confirmations). */
    val message: String? = null,
) {
    val hasImage: Boolean get() = original != null
}
