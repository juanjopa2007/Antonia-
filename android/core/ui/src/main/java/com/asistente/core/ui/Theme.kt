package com.asistente.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta JARVIS: negro profundo, azul eléctrico y cian. */
object JarvisColors {
    val Black = Color(0xFF000000)
    val Background = Color(0xFF02060D)
    val Surface = Color(0xFF07111F)
    val ElectricBlue = Color(0xFF1F6BFF)
    val DeepBlue = Color(0xFF0A2A66)
    val Cyan = Color(0xFF00E5FF)
    val TextPrimary = Color(0xFFE6F6FF)
    val TextSecondary = Color(0xFF7FA6BF)
    val Error = Color(0xFFFF5470)
}

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisColors.Cyan,
    onPrimary = JarvisColors.Black,
    secondary = JarvisColors.ElectricBlue,
    onSecondary = JarvisColors.TextPrimary,
    background = JarvisColors.Background,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.Surface,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.DeepBlue,
    onSurfaceVariant = JarvisColors.TextSecondary,
    error = JarvisColors.Error,
)

/** La aplicación es siempre oscura: el estilo JARVIS no tiene variante clara. */
@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColorScheme, content = content)
}
