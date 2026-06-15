package com.varos.imageenhance.presentation.editor

import android.graphics.Bitmap
import android.net.Uri
import com.varos.imageenhance.domain.model.PipelineSettings

/**
 * MVI contract for the editor screen.
 *
 * - [EditorState] is the single immutable model the UI renders.
 * - [EditorIntent] is the closed set of things the user can do; the View only
 *   ever calls [EditorViewModel.onIntent].
 * - [EditorEffect] is a one-shot side effect (e.g. a transient message) that
 *   isn't part of persistent state.
 */
data class EditorState(
    val original: Bitmap? = null,
    val processed: Bitmap? = null,
    val settings: PipelineSettings = PipelineSettings.EMPTY,
    val selectedFilterId: String = "",
    val isLoading: Boolean = false,
    /** A full-resolution render is in progress (after the seek bar settles). */
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val isEdited: Boolean = false,
) {
    val hasImage: Boolean get() = original != null
}

sealed interface EditorIntent {
    /** A gallery image was chosen. */
    data class PickImage(val uri: Uri) : EditorIntent

    /**
     * Result of the camera capture. The target Uri is held in SavedStateHandle
     * (not the View), so it survives the process death that happens while the
     * camera app is foreground under "don't keep activities".
     */
    data class CameraCaptured(val success: Boolean) : EditorIntent

    /** Switch the active tool tab. */
    data class SelectFilter(val filterId: String) : EditorIntent

    /** Live seek-bar drag: updates the value and renders a fast preview. */
    data class ChangeFilterValue(val filterId: String, val value: Float) : EditorIntent

    /** Seek-bar released: render the final full-resolution result. */
    data object CommitFilters : EditorIntent

    data object Reset : EditorIntent
    data object Save : EditorIntent
}

sealed interface EditorEffect {
    data class ShowMessage(val text: String) : EditorEffect
}
