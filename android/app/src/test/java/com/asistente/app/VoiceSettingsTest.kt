package com.asistente.app

import com.asistente.app.settings.VoiceSettings
import com.asistente.app.settings.toParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSettingsTest {

    @Test
    fun `la velocidad se mantiene dentro de los limites`() {
        assertEquals(0.5f, VoiceSettings.clampRate(0.1f), 0f)
        assertEquals(2f, VoiceSettings.clampRate(9f), 0f)
        assertEquals(1.3f, VoiceSettings.clampRate(1.3f), 0f)
    }

    @Test
    fun `una velocidad no numerica vuelve a la normal`() {
        assertEquals(1f, VoiceSettings.clampRate(Float.NaN), 0f)
    }

    @Test
    fun `los parametros de voz reflejan los ajustes`() {
        val params = VoiceSettings(voiceId = "es-es-x-abc-local", rate = 1.5f).toParams()
        assertEquals("es-es-x-abc-local", params.voiceId)
        assertEquals(1.5f, params.rate, 0f)
    }

    @Test
    fun `por defecto se usa la voz del sistema a velocidad normal`() {
        val params = VoiceSettings().toParams()
        assertNull(params.voiceId)
        assertEquals(1f, params.rate, 0f)
    }
}
