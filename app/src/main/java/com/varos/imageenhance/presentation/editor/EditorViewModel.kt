package com.varos.imageenhance.presentation.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.usecase.CreateCaptureUriUseCase
import com.varos.imageenhance.domain.usecase.EnhanceImageUseCase
import com.varos.imageenhance.domain.usecase.GetFiltersUseCase
import com.varos.imageenhance.domain.usecase.LoadImageUseCase
import com.varos.imageenhance.domain.usecase.SaveImageToGalleryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * MVI ViewModel for the editor. The View sends [EditorIntent]s to [onIntent];
 * the ViewModel is the single owner of [EditorState] and the only place that
 * mutates it, and emits one-shot [EditorEffect]s through [effects].
 *
 * Responsiveness (two-tier rendering):
 *  - while a seek bar is dragged ([EditorIntent.ChangeFilterValue]) we render a
 *    **downscaled preview** — fast, near-instant feedback.
 *  - when the bar settles ([EditorIntent.CommitFilters]) we render the
 *    **full-resolution** result.
 * Both streams use [mapLatest], so a newer value cancels stale work.
 *
 * Process-death survival ("AKM"): the source [Uri], the [PipelineSettings] and
 * the selected tab are persisted in [SavedStateHandle]; on recreation the image
 * is reloaded and the saved edits re-applied, so the user returns to exactly
 * where they were. (Bitmaps are too large to serialize, hence reload-from-Uri.)
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val savedState: SavedStateHandle,
    private val loadImage: LoadImageUseCase,
    private val enhanceImage: EnhanceImageUseCase,
    private val saveImage: SaveImageToGalleryUseCase,
    private val createCaptureUri: CreateCaptureUriUseCase,
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

    /** Full-resolution source and a cheap downscaled copy used for live previews. */
    private var fullSource: Bitmap? = null
    private var previewSource: Bitmap? = null

    private val previewRequests = MutableSharedFlow<PipelineSettings>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val commitRequests = MutableSharedFlow<PipelineSettings>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // Fast preview stream (downscaled source) shown WHILE the finger drags.
        // conflate() makes this "process only the latest": any intermediate slider
        // values that arrive while a render is in flight are dropped, and the most
        // recent one is rendered next — to completion, never cancelled. So every
        // displayed frame reflects the newest finger position with no wasted work.
        viewModelScope.launch {
            previewRequests
                .conflate()
                .collect { settings ->
                    val bmp = previewSource?.let {
                        runCatching { enhanceImage(it, settings) }.getOrNull()
                    }
                    if (bmp != null) _state.value = _state.value.copy(processed = bmp)
                }
        }
        // Final full-resolution stream (debounced so it only runs once settled).
        viewModelScope.launch {
            commitRequests
                .debounce(100)
                .onEach { _state.value = _state.value.copy(isProcessing = true) }
                .mapLatest { settings ->
                    fullSource?.let { runCatching { enhanceImage(it, settings) } }
                }
                .collect { result ->
                    result?.onSuccess { bmp ->
                        _state.value = _state.value.copy(processed = bmp, isProcessing = false)
                    }?.onFailure {
                        _state.value = _state.value.copy(isProcessing = false)
                        _effects.send(EditorEffect.ShowMessage("Couldn't apply filter: ${it.message}"))
                    }
                }
        }
        restoreFromSavedState()
    }

    fun onIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.PickImage -> loadFromUri(intent.uri, restoredSettings = null)
            is EditorIntent.SelectFilter -> {
                _state.value = _state.value.copy(selectedFilterId = intent.filterId)
                savedState[KEY_SELECTED] = intent.filterId
            }
            is EditorIntent.ChangeFilterValue -> changeValue(intent.filterId, intent.value)
            EditorIntent.CommitFilters -> commitRequests.tryEmit(_state.value.settings)
            EditorIntent.Reset -> resetEdits()
            EditorIntent.Save -> save()
        }
    }

    /** A fresh file Uri for the camera to capture into (not an intent — no state change). */
    fun captureUri(): Uri = createCaptureUri()

    private fun changeValue(filterId: String, value: Float) {
        val settings = _state.value.settings.with(filterId, value)
        _state.value = _state.value.copy(settings = settings, isEdited = settings.isModified(filters))
        persistSettings(settings)
        previewRequests.tryEmit(settings) // live preview; CommitFilters renders full-res
    }

    private fun resetEdits() {
        _state.value = _state.value.copy(settings = PipelineSettings.EMPTY, isEdited = false)
        persistSettings(PipelineSettings.EMPTY)
        commitRequests.tryEmit(PipelineSettings.EMPTY)
    }

    private fun save() {
        val bitmap = _state.value.processed ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            runCatching { saveImage(bitmap, "enhanced_${System.currentTimeMillis()}.jpg") }
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false)
                    _effects.send(EditorEffect.ShowMessage("Saved to gallery"))
                }
                .onFailure {
                    _state.value = _state.value.copy(isSaving = false)
                    _effects.send(EditorEffect.ShowMessage("Save failed: ${it.message}"))
                }
        }
    }

    private fun loadFromUri(uri: Uri, restoredSettings: PipelineSettings?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            runCatching { loadImage(uri) }
                .onSuccess { bmp ->
                    fullSource?.recycle()
                    previewSource?.takeIf { it !== fullSource }?.recycle()
                    fullSource = bmp
                    previewSource = bmp.downscaled(PREVIEW_MAX_EDGE)

                    val settings = restoredSettings ?: PipelineSettings.EMPTY
                    _state.value = _state.value.copy(
                        original = bmp,
                        processed = bmp,
                        settings = settings,
                        isLoading = false,
                        isEdited = settings.isModified(filters),
                    )
                    savedState[KEY_URI] = uri.toString()
                    persistSettings(settings)
                    if (settings.isModified(filters)) commitRequests.tryEmit(settings)
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false)
                    _effects.send(EditorEffect.ShowMessage("Couldn't load image: ${it.message}"))
                }
        }
    }

    private fun restoreFromSavedState() {
        val uri = savedState.get<String>(KEY_URI)?.toUri() ?: return
        _state.value = _state.value.copy(
            selectedFilterId = savedState.get<String>(KEY_SELECTED)
                ?: _state.value.selectedFilterId,
        )
        loadFromUri(uri, restoredSettings = readSavedSettings())
    }

    private fun persistSettings(settings: PipelineSettings) {
        savedState[KEY_SETTINGS] = HashMap(settings.values)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSavedSettings(): PipelineSettings {
        val map = savedState.get<HashMap<String, Float>>(KEY_SETTINGS) ?: return PipelineSettings.EMPTY
        return PipelineSettings(map.toMap())
    }

    private fun Bitmap.downscaled(maxEdge: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxEdge) return this
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    override fun onCleared() {
        fullSource?.recycle()
        previewSource?.takeIf { it !== fullSource }?.recycle()
        super.onCleared()
    }

    private companion object {
        const val KEY_URI = "editor.uri"
        const val KEY_SETTINGS = "editor.settings"
        const val KEY_SELECTED = "editor.selected"

        /** Live-preview working size; small enough to filter in a few ms. */
        const val PREVIEW_MAX_EDGE = 1080
    }
}
