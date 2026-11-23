package com.asistente.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSettingsTest {

    private fun url(value: String) = ConnectionSettings(serverUrl = value, token = "t").normalizedUrl()

    @Test
    fun `quita espacios y barras finales`() {
        assertEquals("https://antonia.run.app", url("  https://antonia.run.app/  "))
        assertEquals("https://antonia.run.app", url("https://antonia.run.app///"))
    }

    @Test
    fun `conserva una ruta base`() {
        assertEquals("https://ejemplo.com/api", url("https://ejemplo.com/api/"))
    }

    @Test
    fun `acepta https en mayusculas`() {
        assertEquals("HTTPS://Antonia.run.app", url("HTTPS://Antonia.run.app"))
    }

    @Test
    fun `rechaza http y direcciones sin esquema`() {
        assertNull(url("http://antonia.run.app"))
        assertNull(url("antonia.run.app"))
        assertNull(url(""))
        assertNull(url("   "))
    }

    @Test
    fun `rechaza direcciones sin servidor o mal formadas`() {
        assertNull(url("https://"))
        assertNull(url("https:///ruta"))
        assertNull(url("https://exa mple.com"))
    }

    @Test
    fun `solo esta configurado con direccion valida y token`() {
        assertTrue(ConnectionSettings("https://a.example.com", "abc").isConfigured)
        assertFalse(ConnectionSettings("https://a.example.com", "  ").isConfigured)
        assertFalse(ConnectionSettings("http://a.example.com", "abc").isConfigured)
        assertFalse(ConnectionSettings().isConfigured)
    }
}
