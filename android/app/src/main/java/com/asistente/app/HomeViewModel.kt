package com.asistente.app

import androidx.lifecycle.ViewModel
import com.asistente.core.ui.OrbState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Entrega 1: solo gestiona el estado visual del círculo central.
 * La entrega 2 conecta este estado con el reconocimiento y la síntesis de voz.
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(OrbState.Idle)
    val state: StateFlow<OrbState> = _state.asStateFlow()

    fun onOrbTapped() {
        _state.update { it.onOrbTapped() }
    }
}
