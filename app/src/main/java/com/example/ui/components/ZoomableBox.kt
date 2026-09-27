package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A container that provides multi-touch pinch-to-zoom, single-finger panning when zoomed,
 * double-tap to zoom/reset, and accessible floating zoom controls for images and PDF documents.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 6.0f,
    resetKey: Any? = null,
    showControls: Boolean = true,
    showHintInitially: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1.0f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val rotationAnim = remember { Animatable(0f) }

    var boxSize by remember { mutableStateOf(Size.Zero) }
    var showHint by remember { mutableStateOf(showHintInitially) }

    // Auto-dismiss hint after 3.5 seconds
    LaunchedEffect(Unit) {
        if (showHintInitially) {
            delay(3500)
            showHint = false
        }
    }

    // Reset zoom and rotation when resetKey changes (e.g., page navigation or file switch)
    LaunchedEffect(resetKey) {
        if (scaleAnim.value != 1.0f || offsetXAnim.value != 0f || offsetYAnim.value != 0f || rotationAnim.value != 0f) {
            scaleAnim.snapTo(1.0f)
            offsetXAnim.snapTo(0f)
            offsetYAnim.snapTo(0f)
            rotationAnim.snapTo(0f)
        }
    }

    val currentScale = scaleAnim.value
    val isZoomed = currentScale > 1.05f

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val clampedScale = targetScale.coerceIn(minScale, maxScale)
        val maxOffsetX = ((boxSize.width * clampedScale - boxSize.width) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((boxSize.height * clampedScale - boxSize.height) / 2f).coerceAtLeast(0f)
        val clampedOffset = if (clampedScale <= 1.01f) {
            Offset.Zero
        } else {
            Offset(
                x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
            )
        }

        scope.launch {
            launch {
                scaleAnim.animateTo(
                    clampedScale,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            }
            launch {
                offsetXAnim.animateTo(
                    clampedOffset.x,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            }
            launch {
                offsetYAnim.animateTo(
                    clampedOffset.y,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clipToBounds()
            .onSizeChanged { boxSize = it.toSize() }
            .pointerInput(boxSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (boxSize.width <= 0f || boxSize.height <= 0f) return@detectTapGestures
                        showHint = false
                        val curScale = scaleAnim.value
                        val targetScale = if (curScale > 1.15f) 1.0f else 2.5f.coerceAtMost(maxScale)
                        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)

                        val targetOffset = if (targetScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            val maxOffsetX = ((boxSize.width * targetScale - boxSize.width) / 2f).coerceAtLeast(0f)
                            val maxOffsetY = ((boxSize.height * targetScale - boxSize.height) / 2f).coerceAtLeast(0f)
                            val shift = -(tapOffset - center) * (targetScale - 1f)
                            Offset(
                                x = shift.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = shift.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }

                        animateTo(targetScale, targetOffset)
                    }
                )
            }
            .pointerInput(boxSize) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    if (boxSize.width <= 0f || boxSize.height <= 0f) return@detectTransformGestures
                    val curScale = scaleAnim.value
                    if (zoom != 1.0f || curScale > 1.01f) {
                        showHint = false
                        val newScale = (curScale * zoom).coerceIn(minScale, maxScale)
                        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                        val actualZoomRatio = newScale / curScale
                        val centroidShift = (centroid - center) * (actualZoomRatio - 1f)

                        var newOffset = (Offset(offsetXAnim.value, offsetYAnim.value) + pan) - centroidShift

                        val maxOffsetX = ((boxSize.width * newScale - boxSize.width) / 2f).coerceAtLeast(0f)
                        val maxOffsetY = ((boxSize.height * newScale - boxSize.height) / 2f).coerceAtLeast(0f)

                        if (newScale <= 1.01f) {
                            newOffset = Offset.Zero
                        } else {
                            newOffset = Offset(
                                x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }

                        scope.launch {
                            scaleAnim.snapTo(newScale)
                            offsetXAnim.snapTo(newOffset.x)
                            offsetYAnim.snapTo(newOffset.y)
                        }
                    }
                }
            }
            .testTag("zoomable_content_box"),
        contentAlignment = Alignment.Center
    ) {
        // Scaled, rotated and translated inner content layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    rotationZ = rotationAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                },
            contentAlignment = Alignment.Center,
            content = content
        )

        // Subtle initial gesture hint badge
        AnimatedVisibility(
            visible = showHint && !isZoomed,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pinch fingers or double-tap to zoom",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Floating Zoom HUD Controls
        if (showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .testTag("zoom_controls_bar"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Zoom Out Button
                    IconButton(
                        onClick = {
                            val targetScale = (scaleAnim.value - 0.5f).coerceAtLeast(minScale)
                            val targetOffset = if (targetScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                Offset(offsetXAnim.value, offsetYAnim.value)
                            }
                            animateTo(targetScale, targetOffset)
                        },
                        enabled = currentScale > minScale,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out",
                            tint = if (currentScale > minScale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Percentage Badge / Reset tap
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isZoomed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable {
                                if (isZoomed) {
                                    animateTo(1.0f, Offset.Zero)
                                } else {
                                    animateTo(2.0f, Offset.Zero)
                                }
                            }
                            .testTag("zoom_percentage_text")
                    ) {
                        Text(
                            text = "${(currentScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isZoomed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Zoom In Button
                    IconButton(
                        onClick = {
                            val targetScale = (scaleAnim.value + 0.5f).coerceAtMost(maxScale)
                            animateTo(targetScale, Offset(offsetXAnim.value, offsetYAnim.value))
                        },
                        enabled = currentScale < maxScale,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom In",
                            tint = if (currentScale < maxScale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Rotate Right Button (90 degrees)
                    IconButton(
                        onClick = {
                            scope.launch {
                                val nextRot = (rotationAnim.value + 90f) % 360f
                                rotationAnim.animateTo(
                                    nextRot,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                                )
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("zoom_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset / Fit Screen Button (Visible when zoomed or rotated)
                    val isModified = isZoomed || rotationAnim.value != 0f
                    AnimatedVisibility(
                        visible = isModified,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = {
                                animateTo(1.0f, Offset.Zero)
                                scope.launch {
                                    rotationAnim.animateTo(0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("zoom_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Zoom & Rotation",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
