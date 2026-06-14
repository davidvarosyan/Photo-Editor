package com.varos.imageenhance.ui.editor.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Interactive before/after comparison. The processed image is drawn full-frame;
 * the original is clipped to the left of a draggable vertical divider, so
 * dragging "wipes" between the two. This lets the user directly judge whether an
 * enhancement actually helped.
 */
@Composable
fun BeforeAfterSlider(
    original: Bitmap,
    processed: Bitmap,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        var handleX by remember(widthPx) { mutableFloatStateOf(widthPx / 2f) }
        val fraction = (handleX / widthPx).coerceIn(0f, 1f)

        // After (processed): full frame underneath.
        Image(
            bitmap = processed.asImageBitmap(),
            contentDescription = "Enhanced image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // Before (original): clipped to the divider position, drawn on top.
        Image(
            bitmap = original.asImageBitmap(),
            contentDescription = "Original image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                },
        )

        // Divider line + draggable grip.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        handleX = (handleX + dragAmount).coerceIn(0f, widthPx)
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val x = size.width * fraction
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(x - 1.5f, 0f),
                            size = Size(3f, size.height),
                        )
                    },
            )
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((widthPx * fraction).roundToInt() - 22, 0) }
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CompareArrows,
                    contentDescription = "Drag to compare",
                    tint = Color.Black,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
