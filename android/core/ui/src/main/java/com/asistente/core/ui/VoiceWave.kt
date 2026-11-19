package com.asistente.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import kotlin.math.PI

/** Barras de onda de voz. La energía (0..1) viene del micrófono o del volumen de la voz sintética. */
@Composable
fun VoiceWave(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val heights = waveHeights(barCount, phase, amplitude)
        val slot = size.width / barCount
        val barWidth = slot * 0.55f
        val brush = Brush.verticalGradient(listOf(JarvisColors.Cyan, JarvisColors.ElectricBlue))
        heights.forEachIndexed { i, h ->
            val barHeight = size.height * h
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
