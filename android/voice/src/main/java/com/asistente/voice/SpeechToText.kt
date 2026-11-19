package com.asistente.voice

/** Eventos del reconocimiento de voz. Siempre se entregan en el hilo principal. */
sealed interface SttEvent {
    /** El micrófono está listo: ya se puede hablar. */
    data object Ready : SttEvent

    /** Volumen de la voz, normalizado entre 0 y 1. */
    data class Level(val value: Float) : SttEvent

    /** Texto parcial mientras el usuario todavía habla. */
    data class Partial(val text: String) : SttEvent

    /** Texto definitivo. Puede estar vacío. */
    data class Final(val text: String) : SttEvent

    data class Failed(val error: SttError) : SttEvent
}

enum class SttError {
    /** No se oyó nada o no se entendió. */
    NoSpeech,

    /** El reconocedor está ocupado con otra petición. */
    Busy,

    /** Falta el permiso de micrófono. */
    NoPermission,

    /** Problema de red del servicio de reconocimiento. */
    Network,

    /** No hay servicio de reconocimiento de voz en el móvil. */
    Unavailable,

    Other,
}

/** Reconocimiento de voz. Solo escucha entre [start] y el resultado, [stop] o [cancel]. */
interface SpeechToText {
    val isAvailable: Boolean

    /** Empieza a escuchar. Cualquier sesión anterior se cancela. */
    fun start(listener: (SttEvent) -> Unit)

    /** Deja de escuchar y entrega lo entendido hasta ahora como [SttEvent.Final]. */
    fun stop()

    /** Deja de escuchar y descarta el resultado. */
    fun cancel()

    fun release()
}
