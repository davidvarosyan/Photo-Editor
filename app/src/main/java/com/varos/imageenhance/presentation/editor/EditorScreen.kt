package com.varos.imageenhance.presentation.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varos.imageenhance.domain.model.ImageFilter
import com.varos.imageenhance.presentation.editor.components.BeforeAfterSlider
import com.varos.imageenhance.presentation.editor.components.ToolPanel
import kotlinx.coroutines.flow.collectLatest

/**
 * Editor View (MVI): renders purely from [EditorState] and forwards every user
 * action as an [EditorIntent]. One-shot [EditorEffect]s drive the snackbar. The
 * photo picker uses the modern PickVisualMedia contract (no storage permission).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.onIntent(EditorIntent.PickImage(it)) } }

    // The capture target Uri lives in the ViewModel's SavedStateHandle, so it
    // survives process death while the camera app is foreground.
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success -> viewModel.onIntent(EditorIntent.CameraCaptured(success)) }

    fun launchPicker() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    fun launchCamera() = camera.launch(viewModel.captureUri())

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditorEffect.ShowMessage -> snackbarHost.showSnackbar(effect.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enhance") },
                actions = {
                    if (state.hasImage) {
                        IconButton(onClick = ::launchCamera) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = "Take photo")
                        }
                        TextButton(onClick = ::launchPicker) { Text("Replace") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.hasImage -> EmptyState(
                    isLoading = state.isLoading,
                    onPick = ::launchPicker,
                    onCamera = ::launchCamera,
                )

                else -> LoadedEditor(
                    state = state,
                    filters = viewModel.filters,
                    onIntent = viewModel::onIntent,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(isLoading: Boolean, onPick: () -> Unit, onCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading image…")
        } else {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Improve readability of receipts, notes, labels and low-quality photos.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPick) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Choose from gallery")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCamera) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Take a photo")
            }
        }
    }
}

@Composable
private fun LoadedEditor(
    state: EditorState,
    filters: List<ImageFilter>,
    onIntent: (EditorIntent) -> Unit,
) {
    val original = state.original ?: return
    val processed = state.processed ?: original

    Column(modifier = Modifier.fillMaxSize()) {
        // Comparison viewport fills all remaining space. Because the panel below
        // has a fixed height, this preview keeps the SAME size across tabs.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            BeforeAfterSlider(
                original = original,
                processed = processed,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
            Text(
                "Drag to compare",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            ToolPanel(
                filters = filters,
                selectedFilterId = state.selectedFilterId,
                onSelect = { onIntent(EditorIntent.SelectFilter(it)) },
                settings = state.settings,
                onChange = { id, value -> onIntent(EditorIntent.ChangeFilterValue(id, value)) },
                onCommit = { onIntent(EditorIntent.CommitFilters) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onIntent(EditorIntent.Reset) },
                    enabled = state.isEdited && !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Reset")
                }
                Button(
                    // Not gated on isProcessing: Save re-renders from the original,
                    // independent of the live preview, so it must not blink while
                    // a seek bar is being dragged.
                    onClick = { onIntent(EditorIntent.Save) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}
