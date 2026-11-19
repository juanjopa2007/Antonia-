package com.asistente.voice

/** Una voz en español instalada en el móvil. */
data class SpanishVoice(
    val id: String,
    val label: String,
)

/** Ajustes aplicados en cada locución. [voiceId] nulo significa «la voz por defecto del sistema». */
data class SpeechParams(
    val voiceId: String? = null,
    val rate: Float = 1f,
)

enum class SpeakOutcome {
    /** La locución terminó sola. */
    Completed,

    /** No se pudo pronunciar. */
    Failed,
}

/** Síntesis de voz. Todo se usa y responde en el hilo principal. */
interface TextToSpeechEngine {
    /** Voces en español disponibles. Espera a que el motor esté listo; vacío si no hay motor. */
    suspend fun voices(): List<SpanishVoice>

    /**
     * Pronuncia [text]. Sustituye a cualquier locución en curso.
     * [onFinished] solo se llama cuando termina sola o falla, nunca tras [stop].
     */
    fun speak(text: String, params: SpeechParams, onFinished: (SpeakOutcome) -> Unit)

    /** Interrumpe la locución en curso sin avisar a [speak]. */
    fun stop()

    fun release()
}
