package com.asistente.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * Círculo central animado. Es el botón principal: un toque inicia o termina la conversación.
 *
 * @param amplitude energía de la voz (0..1) que hace "respirar" el círculo; solo se aplica
 * al escuchar o hablar.
 */
@Composable
fun JarvisOrb(
    state: OrbState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    description: String = "Botón del asistente",
    stateLabel: String = "",
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val speed = when (state) {
        OrbState.Idle -> 24_000
        OrbState.Listening -> 9_000
        OrbState.Thinking -> 3_500
        OrbState.Speaking -> 12_000
    }
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(speed, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (state == OrbState.Idle) 3_200 else 1_400, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                if (stateLabel.isNotEmpty()) stateDescription = stateLabel
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawOrb(state, rotation, pulse, amplitude.coerceIn(0f, 1f))
        }
    }
}

private fun DrawScope.drawOrb(state: OrbState, rotation: Float, pulse: Float, amplitude: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = size.minDimension / 2f
    val active = state != OrbState.Idle
    val energy = if (state == OrbState.Listening || state == OrbState.Speaking) amplitude else 0f

    // Resplandor exterior.
    val glowStrength = (if (active) 0.55f else 0.25f) + 0.25f * pulse + 0.2f * energy
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                JarvisColors.Cyan.copy(alpha = glowStrength.coerceIn(0f, 1f)),
                JarvisColors.ElectricBlue.copy(alpha = (glowStrength * 0.45f).coerceIn(0f, 1f)),
                Color.Transparent,
            ),
            center = center,
            radius = maxRadius,
        ),
        radius = maxRadius,
        center = center,
    )

    // Núcleo: crece con la voz y late suavemente.
    val coreRadius = maxRadius * (0.34f + 0.05f * pulse + 0.12f * energy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.9f), JarvisColors.Cyan, JarvisColors.ElectricBlue),
            center = center,
            radius = coreRadius,
        ),
        radius = coreRadius,
        center = center,
    )

    // Anillos con tramos, girando en sentidos opuestos.
    val ringWidth = maxRadius * 0.025f
    drawSegmentedRing(center, maxRadius * 0.58f, ringWidth, rotation, segments = 3, sweep = 70f, color = JarvisColors.Cyan)
    drawSegmentedRing(center, maxRadius * 0.72f, ringWidth * 0.8f, -rotation * 1.4f, segments = 5, sweep = 40f, color = JarvisColors.ElectricBlue)
    drawSegmentedRing(center, maxRadius * 0.86f, ringWidth * 0.6f, rotation * 0.6f, segments = 12, sweep = 14f, color = JarvisColors.Cyan.copy(alpha = 0.7f))

    // Anillo fino continuo en el borde.
    drawCircle(
        color = JarvisColors.ElectricBlue.copy(alpha = 0.55f),
        radius = maxRadius * 0.96f,
        center = center,
        style = Stroke(width = ringWidth * 0.4f),
    )
}

private fun DrawScope.drawSegmentedRing(
    center: Offset,
    radius: Float,
    width: Float,
    rotationDegrees: Float,
    segments: Int,
    sweep: Float,
    color: Color,
) {
    rotate(degrees = rotationDegrees, pivot = center) {
        val step = 360f / segments
        for (i in 0 until segments) {
            drawArc(
                color = color,
                startAngle = i * step,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
    }
}
