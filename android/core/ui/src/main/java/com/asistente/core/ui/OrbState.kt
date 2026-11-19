package com.asistente.core.ui

/** Estados visuales del círculo central. */
enum class OrbState {
    /** En reposo: el micrófono está apagado. */
    Idle,

    /** Escuchando al usuario. */
    Listening,

    /** Claude está procesando la petición. */
    Thinking,

    /** El asistente está hablando. */
    Speaking,
}
