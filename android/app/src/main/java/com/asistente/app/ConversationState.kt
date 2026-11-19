package com.asistente.app

import com.asistente.core.ui.OrbState

/**
 * Qué hace el botón central según el estado actual.
 *
 * Función pura, sin dependencias de Android, para poder probarla con tests unitarios.
 */
fun OrbState.onOrbTapped(): OrbState = when (this) {
    // Empezar a escuchar.
    OrbState.Idle -> OrbState.Listening
    // Terminar de hablar: se envía lo dicho y se espera la respuesta.
    OrbState.Listening -> OrbState.Thinking
    // Cancelar la petición en curso.
    OrbState.Thinking -> OrbState.Idle
    // Interrumpir la respuesta hablada y volver a escuchar.
    OrbState.Speaking -> OrbState.Listening
}
