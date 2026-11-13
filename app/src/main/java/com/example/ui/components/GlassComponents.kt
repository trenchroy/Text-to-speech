package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.ToastMessage
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo600
import com.example.ui.theme.LocalArLabsColors
import com.example.ui.theme.VioletAccent
import java.util.Locale

@Composable
fun StudioBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val ext = LocalArLabsColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ext.bgGradientTop, ext.bgGradientBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topGlowAlpha = if (ext.isDark) 0.22f else 0.14f
            val bottomGlowAlpha = if (ext.isDark) 0.16f else 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Indigo500.copy(alpha = topGlowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.08f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.15f, size.height * 0.08f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanAccent.copy(alpha = bottomGlowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.85f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.9f, size.height * 0.85f)
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = LocalArLabsColors.current
    val bg = if (elevated) ext.glassSurfaceElevated else ext.glassSurface
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = bg,
        border = BorderStroke(1.dp, ext.glassBorder),
        tonalElevation = if (elevated) 6.dp else 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun CustomToastBanner(
    toast: ToastMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalArLabsColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180))
        ) {
            if (toast != null) {
                val accentColor = if (toast.isError) MaterialTheme.colorScheme.error else ext.success
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_toast_banner"),
                    shape = RoundedCornerShape(18.dp),
                    color = ext.glassSurfaceElevated,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (toast.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = toast.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Band karein",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedEqualizerBars(
    isActive: Boolean,
    color: Color = Indigo400,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b4"
    )

    val heights = if (isActive) listOf(bar1, bar2, bar3, bar4) else listOf(0.35f, 0.6f, 0.45f, 0.3f)

    Row(
        modifier = modifier.height(18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16.dp * frac).coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
fun DualProgressSeekBar(
    playbackFraction: Float,
    bufferedFraction: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val bufferColor = Indigo400.copy(alpha = 0.42f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekFraction(frac)
                    }
                }
            }
    ) {
        val barHeight = 7.dp.toPx()
        val cy = size.height / 2f
        val top = cy - barHeight / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)

        // Base track
        drawRoundRect(
            color = trackBg,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius
        )

        // Buffered-ahead progress
        val bufWidth = (size.width * bufferedFraction.coerceIn(0f, 1f))
        if (bufWidth > 0f) {
            drawRoundRect(
                color = bufferColor,
                topLeft = Offset(0f, top),
                size = Size(bufWidth, barHeight),
                cornerRadius = radius
            )
        }

        // Current playback progress
        val playWidth = (size.width * playbackFraction.coerceIn(0f, 1f))
        if (playWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Indigo600, Indigo400, CyanAccent)),
                topLeft = Offset(0f, top),
                size = Size(playWidth, barHeight),
                cornerRadius = radius
            )
        }

        // Thumb dot
        val thumbX = playWidth.coerceIn(7.dp.toPx(), (size.width - 7.dp.toPx()).coerceAtLeast(7.dp.toPx()))
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(thumbX, cy)
        )
        drawCircle(
            color = Indigo500,
            radius = 4.5.dp.toPx(),
            center = Offset(thumbX, cy)
        )
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
