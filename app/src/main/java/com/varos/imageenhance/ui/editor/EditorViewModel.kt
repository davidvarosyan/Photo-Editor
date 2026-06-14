package com.varos.imageenhance.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.domain.model.PipelineSettings
import com.varos.imageenhance.domain.usecase.CreateCaptureUriUseCase
import com.varos.imageenhance.domain.usecase.EnhanceImageUseCase
import com.varos.imageenhance.domain.usecase.GetFiltersUseCase
import com.varos.imageenhance.domain.usecase.LoadImageUseCase
import com.varos.imageenhance.domain.usecase.SaveImageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the editor state and drives the (background) enhancement pipeline through
 * use cases. State is a single [MutableStateFlow] of [EditorUiState] mutated via
 * intent-style methods the UI calls.
 *
 * Responsiveness: every settings change updates state instantly (so seek bars
 * feel live), while the expensive processing runs off a debounced flow using
 * [mapLatest], so a newer parameter value cancels stale in-flight work.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val loadImage: LoadImageUseCase,
    private val enhanceImage: EnhanceImageUseCase,
    private val saveImage: SaveImageUseCase,
    private val createCaptureUri: CreateCaptureUriUseCase,
    getFilters: GetFiltersUseCase,
) : ViewModel() {

    /** Registered functionals, used by the UI to build its tabs/controls. */
    val filters: List<ImageFilter> = getFilters()

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state
                .map { it.settings to it.original }
                .distinctUntilChanged()
                .drop(1) // skip the initial empty state
                .debounce(120)
                .onEach { _state.value = _state.value.copy(isProcessing = true) }
                .mapLatest { (settings, original) ->
                    original?.let { runCatching { enhanceImage(it, settings) } }
                }
                .collect { result ->
                    if (result == null) return@collect
                    result.onSuccess { bmp ->
                        _state.value = _state.value.copy(processed = bmp, isProcessing = false)
                    }.onFailure {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            message = "Couldn't apply filter: ${it.message}",
                        )
                    }
                }
        }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            runCatching { loadImage(uri) }
                .onSuccess { bmp ->
                    _state.value = EditorUiState(original = bmp, processed = bmp, isLoading = false)
                }
                .onFailure {
                    android.util.Log.e("EditorViewModel", "Image load failed for $uri", it)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        message = "Couldn't load image: ${it.message}",
                    )
                }
        }
    }

    /** Returns a fresh file Uri for the camera to write a captured photo into. */
    fun captureUri(): Uri = createCaptureUri()

    /** Live update from a tool's seek bar / toggle. */
    fun onFilterValueChanged(filterId: String, value: Float) {
        _state.value = _state.value.copy(settings = _state.value.settings.with(filterId, value))
    }

    fun onReset() {
        _state.value = _state.value.copy(settings = PipelineSettings.EMPTY)
    }

    fun onSave() {
        val bitmap = _state.value.processed ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            runCatching { saveImage(bitmap, "enhanced_${System.currentTimeMillis()}.jpg") }
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false, message = "Saved to gallery")
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = "Save failed: ${it.message}",
                    )
                }
        }
    }

    fun onMessageShown() {
        _state.value = _state.value.copy(message = null)
    }

    /** True when any filter is set away from its neutral value. */
    fun isEdited(state: EditorUiState): Boolean = state.settings.isModified(filters)
}
