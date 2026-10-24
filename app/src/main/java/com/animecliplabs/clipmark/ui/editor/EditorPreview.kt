package com.animecliplabs.clipmark.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.analysis.Readability
import com.animecliplabs.clipmark.core.layout.WatermarkLayout
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import com.animecliplabs.clipmark.image.WatermarkRenderer
import com.animecliplabs.clipmark.ui.batch.failureText

@Composable
fun EditorPreview(
    preview: PreviewState,
    style: WatermarkStyle,
    smartAnchor: Anchor?,
    readability: Readability,
    showOriginal: Boolean,
    guides: Guides?,
    renderer: WatermarkRenderer,
    onDrag: (centerX: Float, centerY: Float, width: Float, height: Float, threshold: Float) -> Unit,
    onDragEnd: () -> Unit,
    onHoldOriginal: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(targetState = preview, modifier = modifier, label = "preview") { state ->
        when (state) {
            PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is PreviewState.Failed -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.BrokenImage, contentDescription = null, modifier = Modifier.size(48.dp))
                Text(failureText(state.kind), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.preview_failed_hint),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is PreviewState.Ready -> FittedImage(
                bitmap = state.bitmap, style = style, smartAnchor = smartAnchor, readability = readability,
                showOriginal = showOriginal, guides = guides, renderer = renderer,
                onDrag = onDrag, onDragEnd = onDragEnd, onHoldOriginal = onHoldOriginal,
            )
        }
    }
}

@Composable
private fun FittedImage(
    bitmap: Bitmap,
    style: WatermarkStyle,
    smartAnchor: Anchor?,
    readability: Readability,
    showOriginal: Boolean,
    guides: Guides?,
    renderer: WatermarkRenderer,
    onDrag: (Float, Float, Float, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onHoldOriginal: (Boolean) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentStyle by rememberUpdatedState(style)
    val currentSmart by rememberUpdatedState(smartAnchor)
    val guideColor = MaterialTheme.colorScheme.primary
    val a11yPreview = stringResource(R.string.a11y_preview)

    LaunchedEffect(guides?.snapped) {
        if (guides?.snapped != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        val aspect = bitmap.width / bitmap.height.toFloat()
        val boxAspect = maxWidth / maxHeight
        val (w, h) = if (boxAspect > aspect) (maxHeight * aspect) to maxHeight else maxWidth to (maxWidth / aspect)

        Box(Modifier.size(w, h).clip(MaterialTheme.shapes.small)) {
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = a11yPreview }
                    .pointerInput(renderer) {
                        val hitSlop = with(density) { 28.dp.toPx() }
                        val snapDistance = with(density) { 22.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w0 = size.width.toFloat()
                            val h0 = size.height.toFloat()
                            val p = renderer.place(w0, h0, currentStyle, currentSmart)
                            val onWatermark = p != null &&
                                down.position.x in (p.left - hitSlop)..(p.right + hitSlop) &&
                                down.position.y in (p.top - hitSlop)..(p.bottom + hitSlop)
                            if (onWatermark) {
                                // Drag the watermark; accumulate the raw finger position so the user
                                // can always pull it back out of a snap.
                                var center = Offset(p.centerX, p.centerY)
                                down.consume()
                                drag(down.id) { change ->
                                    center += change.positionChange()
                                    change.consume()
                                    onDrag(center.x, center.y, w0, h0, snapDistance)
                                }
                                onDragEnd()
                            } else {
                                // Press and hold anywhere else to see the untouched original.
                                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                }
                                if (released == null) {
                                    onHoldOriginal(true)
                                    waitForUpOrCancellation()
                                    onHoldOriginal(false)
                                }
                            }
                        }
                    },
            ) {
                if (showOriginal) return@Canvas
                if (guides != null) {
                    val safe = WatermarkLayout.safeArea(size.width, size.height, style)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    drawRect(
                        color = Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(safe.left, safe.top),
                        size = Size(safe.right - safe.left, safe.bottom - safe.top),
                        style = Stroke(width = 1.5f, pathEffect = dash),
                    )
                    guides.x?.let { drawLine(guideColor, Offset(it, 0f), Offset(it, size.height), strokeWidth = 2f) }
                    guides.y?.let { drawLine(guideColor, Offset(0f, it), Offset(size.width, it), strokeWidth = 2f) }
                }
                val placement = renderer.place(size.width, size.height, style, smartAnchor) ?: return@Canvas
                drawIntoCanvas { renderer.draw(it.nativeCanvas, placement, style, readability) }
            }
            AnimatedVisibility(
                visible = showOriginal,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                    Text(
                        stringResource(R.string.original_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
