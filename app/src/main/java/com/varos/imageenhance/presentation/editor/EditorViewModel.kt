package com.varos.imageenhance.presentation.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.policy.MemoryPolicy
import com.varos.imageenhance.domain.usecase.CreateCaptureUriUseCase
import com.varos.imageenhance.domain.usecase.EnhanceImageUseCase
import com.varos.imageenhance.domain.usecase.ExportImageUseCase
import com.varos.imageenhance.domain.usecase.GetFiltersUseCase
import com.varos.imageenhance.domain.usecase.LoadImageUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the editor. The View sends [EditorIntent]s to [onIntent];
 * the ViewModel is the single owner of [EditorState] and the only place that
 * mutates it, and emits one-shot [EditorEffect]s through [effects].
 *
 * Responsiveness: each seek-bar change renders the full (heap-bounded) working
 * bitmap on the GPU via a single conflated stream — fast enough that no separate
 * downscaled-preview tier is needed; conflate() keeps only the latest value.
 *
 * Process-death survival ("AKM"): the source [Uri], the [PipelineSettings] and
 * the selected tab are persisted in [SavedStateHandle]; on recreation the image
 * is reloaded and the saved edits re-applied, so the user returns to exactly
 * where they were. (Bitmaps are too large to serialize, hence reload-from-Uri.)
 */
class EditorViewModel(
    private val savedState: SavedStateHandle,
    private val loadImage: LoadImageUseCase,
    private val enhanceImage: EnhanceImageUseCase,
    private val exportImage: ExportImageUseCase,
    private val createCaptureUri: CreateCaptureUriUseCase,
    private val memoryPolicy: MemoryPolicy,
    getFilters: GetFiltersUseCase,
) : ViewModel() {

    /** Registered functionals; the UI builds its tabs/controls from these. */
    val filters: List<ImageFilter> = getFilters()

    private val _state = MutableStateFlow(
        EditorState(selectedFilterId = filters.firstOrNull()?.id.orEmpty()),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Memory-bounded working source (high-res, but safe from OOM). */
    private var fullSource: Bitmap? = null

    /** Original image location, kept so Save can re-render it at full resolution. */
    private var sourceUri: Uri? = null

    private val renderRequests = MutableSharedFlow<PipelineSettings>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // Single render stream. The GPU processes the full (heap-bounded) working
        // bitmap on every change, so there's no separate downscaled preview tier.
        // conflate() => "process only the latest": intermediate values that arrive
        // during a fast drag are dropped, and the newest renders to completion
        // (never cancelled), so the shown frame always matches the finger.
        viewModelScope.launch {
            renderRequests
                .conflate()
                .collect { settings ->
                    reduce { it.copy(isProcessing = true) }
                    val result = fullSource?.let { runCatching { enhanceImage(it, settings) } }
                    when {
                        result == null -> reduce { it.copy(isProcessing = false) }
                        else -> result
                            .onSuccess { bmp -> reduce { it.copy(processed = bmp, isProcessing = false) } }
                            .onFailure {
                                reduce { it.copy(isProcessing = false) }
                                _effects.send(EditorEffect.ShowMessage("Couldn't apply filter: ${it.message}"))
                            }
                    }
                }
        }
        restoreFromSavedState()
    }

    fun onIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.PickImage -> loadFromUri(intent.uri, restoredSettings = null)
            is EditorIntent.CameraCaptured -> onCameraCaptured(intent.success)
            is EditorIntent.SelectFilter -> {
                reduce { it.copy(selectedFilterId = intent.filterId) }
                savedState[KEY_SELECTED] = intent.filterId
            }
            is EditorIntent.ChangeFilterValue -> changeValue(intent.filterId, intent.value)
            // Finger-up: a final render to guarantee the last value is applied.
            EditorIntent.CommitFilters -> renderRequests.tryEmit(_state.value.settings)
            EditorIntent.Reset -> resetEdits()
            EditorIntent.Save -> save()
        }
    }

    /**
     * A fresh file Uri for the camera to capture into. Persisted in
     * SavedStateHandle so it survives process death while the camera is open;
     * the result comes back via [EditorIntent.CameraCaptured].
     */
    fun captureUri(): Uri {
        val uri = createCaptureUri()
        savedState[KEY_PENDING_CAPTURE] = uri.toString()
        return uri
    }

    private fun onCameraCaptured(success: Boolean) {
        val pending = savedState.get<String>(KEY_PENDING_CAPTURE)?.toUri()
        savedState[KEY_PENDING_CAPTURE] = null
        if (success && pending != null) loadFromUri(pending, restoredSettings = null)
    }

    private fun changeValue(filterId: String, value: Float) {
        val settings = _state.value.settings.with(filterId, value)
        reduce { it.copy(settings = settings, isEdited = settings.isModified(filters)) }
        persistSettings(settings)
        renderRequests.tryEmit(settings) // live, full-res, latest-only
    }

    private fun resetEdits() {
        reduce { it.copy(settings = PipelineSettings.EMPTY, isEdited = false) }
        persistSettings(PipelineSettings.EMPTY)
        renderRequests.tryEmit(PipelineSettings.EMPTY)
    }

    private fun save() {
        val uri = sourceUri ?: return
        val settings = _state.value.settings
        viewModelScope.launch {
            reduce { it.copy(isSaving = true) }
            // Re-render the ORIGINAL at the largest size the heap allows, so the
            // saved file is full resolution (only gigapixel images get downscaled).
            runCatching { exportImage(uri, settings, "enhanced_${System.currentTimeMillis()}.jpg") }
                .onSuccess { result ->
                    reduce { it.copy(isSaving = false) }
                    _effects.send(
                        EditorEffect.ShowMessage("Saved ${result.width}×${result.height} to gallery"),
                    )
                }
                .onFailure {
                    reduce { it.copy(isSaving = false) }
                    _effects.send(EditorEffect.ShowMessage("Save failed: ${it.message}"))
                }
        }
    }

    private fun loadFromUri(uri: Uri, restoredSettings: PipelineSettings?) {
        viewModelScope.launch {
            reduce { it.copy(isLoading = true) }
            // Editing works on a heap-bounded copy — high res but safe from OOM.
            runCatching { loadImage(uri, memoryPolicy.editingMaxPixels()) }
                .onSuccess { bmp ->
                    sourceUri = uri
                    fullSource?.recycle()
                    fullSource = bmp

                    val settings = restoredSettings ?: PipelineSettings.EMPTY
                    reduce {
                        it.copy(
                            original = bmp,
                            processed = bmp,
                            settings = settings,
                            isLoading = false,
                            isEdited = settings.isModified(filters),
                        )
                    }
                    savedState[KEY_URI] = uri.toString()
                    persistSettings(settings)
                    if (settings.isModified(filters)) renderRequests.tryEmit(settings)
                }
                .onFailure {
                    reduce { it.copy(isLoading = false) }
                    _effects.send(EditorEffect.ShowMessage("Couldn't load image: ${it.message}"))
                }
        }
    }

    private fun restoreFromSavedState() {
        val uri = savedState.get<String>(KEY_URI)?.toUri() ?: return
        savedState.get<String>(KEY_SELECTED)?.let { id -> reduce { it.copy(selectedFilterId = id) } }
        loadFromUri(uri, restoredSettings = readSavedSettings())
    }

    /**
     * The single point that mutates state — an atomic compare-and-set (so the
     * render/save/load coroutines never clobber each other's updates). This is
     * the functional "reducer" seam: `(EditorState) -> EditorState`.
     */
    private inline fun reduce(transform: (EditorState) -> EditorState) = _state.update(transform)

    private fun persistSettings(settings: PipelineSettings) {
        savedState[KEY_SETTINGS] = HashMap(settings.values)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSavedSettings(): PipelineSettings {
        val map = savedState.get<HashMap<String, Float>>(KEY_SETTINGS) ?: return PipelineSettings.EMPTY
        return PipelineSettings(map.toMap())
    }

    override fun onCleared() {
        fullSource?.recycle()
        super.onCleared()
    }

    private companion object {
        const val KEY_URI = "editor.uri"
        const val KEY_SETTINGS = "editor.settings"
        const val KEY_SELECTED = "editor.selected"
        const val KEY_PENDING_CAPTURE = "editor.pendingCapture"
    }
}
