package com.asistente.core.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Altura normalizada (0..1) de cada barra de la onda de voz.
 *
 * Es una función pura para poder probarla sin Android.
 *
 * @param count número de barras (>= 1)
 * @param phase fase de la animación en radianes; al avanzar, la onda se desplaza
 * @param amplitude energía de la voz, entre 0 (silencio) y 1 (máximo); se recorta a ese rango
 * @param floor altura mínima de cada barra en reposo, entre 0 y 1
 */
fun waveHeights(count: Int, phase: Float, amplitude: Float, floor: Float = 0.08f): FloatArray {
    require(count >= 1) { "count debe ser >= 1" }
    val amp = amplitude.coerceIn(0f, 1f)
    val minHeight = floor.coerceIn(0f, 1f)
    return FloatArray(count) { i ->
        val position = if (count == 1) 0.5f else i.toFloat() / (count - 1)
        // Envolvente: barras centrales más altas que las de los extremos.
        val envelope = sin(PI.toFloat() * position)
        // Dos ondas con frecuencias distintas para que el movimiento no parezca mecánico.
        val wave = (abs(sin(position * 6f * PI.toFloat() + phase)) +
            abs(sin(position * 11f * PI.toFloat() - phase * 1.7f))) / 2f
        (minHeight + (1f - minHeight) * amp * envelope * wave).coerceIn(minHeight, 1f)
    }
}
