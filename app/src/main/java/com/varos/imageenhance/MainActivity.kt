package com.varos.imageenhance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.varos.imageenhance.ui.editor.EditorScreen
import com.varos.imageenhance.ui.editor.EditorViewModel
import com.varos.imageenhance.ui.theme.PhotoEditorTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Single-activity entry point. The ViewModel and its whole dependency graph are
 * provided by Koin (see [com.varos.imageenhance.di.appModule]).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoEditorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: EditorViewModel = koinViewModel()
                    EditorScreen(viewModel = viewModel)
                }
            }
        }
    }
}
