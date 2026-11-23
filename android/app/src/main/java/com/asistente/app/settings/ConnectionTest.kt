package com.asistente.app.settings

import com.asistente.app.brain.BrainFailure

/** Resultado de «Probar conexión» en Ajustes. */
sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data object Ok : ConnectionTest
    data class Failed(val failure: BrainFailure) : ConnectionTest
}
