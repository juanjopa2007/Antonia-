package com.asistente.app.settings

import com.asistente.voice.SpeechParams

/**
 * Ajustes de voz del usuario.
 *
 * @param voiceId nombre de la voz elegida, o nulo para la voz por defecto del sistema
 * @param rate velocidad de habla; 1 es la normal
 */
data class VoiceSettings(
    val voiceId: String? = null,
    val rate: Float = DEFAULT_RATE,
) {
    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
        const val DEFAULT_RATE = 1f

        /** Mantiene la velocidad dentro de lo permitido y descarta valores no numéricos. */
        fun clampRate(rate: Float): Float =
            if (rate.isNaN()) DEFAULT_RATE else rate.coerceIn(MIN_RATE, MAX_RATE)
    }
}

fun VoiceSettings.toParams(): SpeechParams =
    SpeechParams(voiceId = voiceId, rate = VoiceSettings.clampRate(rate))
