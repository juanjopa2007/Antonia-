package com.asistente.app

import com.asistente.core.ui.OrbState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationStateTest {

    @Test
    fun `tocar en reposo empieza a escuchar`() {
        assertEquals(OrbState.Listening, OrbState.Idle.onOrbTapped())
    }

    @Test
    fun `tocar mientras escucha termina y pasa a pensar`() {
        assertEquals(OrbState.Thinking, OrbState.Listening.onOrbTapped())
    }

    @Test
    fun `tocar mientras piensa cancela la peticion`() {
        assertEquals(OrbState.Idle, OrbState.Thinking.onOrbTapped())
    }

    @Test
    fun `tocar mientras habla interrumpe y vuelve a escuchar`() {
        assertEquals(OrbState.Listening, OrbState.Speaking.onOrbTapped())
    }

    @Test
    fun `todos los estados tienen transicion`() {
        OrbState.entries.forEach { it.onOrbTapped() }
    }
}
