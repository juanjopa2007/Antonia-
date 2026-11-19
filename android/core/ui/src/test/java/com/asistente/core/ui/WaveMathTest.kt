package com.asistente.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WaveMathTest {

    @Test
    fun `devuelve una altura por barra`() {
        assertEquals(24, waveHeights(count = 24, phase = 0f, amplitude = 0.5f).size)
    }

    @Test
    fun `en silencio todas las barras estan en el minimo`() {
        val heights = waveHeights(count = 16, phase = 1.3f, amplitude = 0f, floor = 0.1f)
        assertTrue(heights.all { it == 0.1f })
    }

    @Test
    fun `las alturas siempre quedan entre el minimo y uno`() {
        for (phase in listOf(0f, 0.7f, 3.14f, 12.5f)) {
            for (amplitude in listOf(-1f, 0f, 0.4f, 1f, 5f)) {
                val heights = waveHeights(count = 32, phase = phase, amplitude = amplitude)
                assertTrue(heights.all { it in 0.08f..1f })
            }
        }
    }

    @Test
    fun `mas amplitud produce barras mas altas`() {
        val low = waveHeights(count = 32, phase = 0.5f, amplitude = 0.2f).sum()
        val high = waveHeights(count = 32, phase = 0.5f, amplitude = 0.9f).sum()
        assertTrue(high > low)
    }

    @Test
    fun `los extremos son mas bajos que el centro`() {
        val heights = waveHeights(count = 33, phase = 0.9f, amplitude = 1f)
        assertEquals(0.08f, heights.first(), 1e-4f)
        assertEquals(0.08f, heights.last(), 1e-4f)
    }

    @Test
    fun `una sola barra no falla`() {
        assertEquals(1, waveHeights(count = 1, phase = 0f, amplitude = 1f).size)
    }

    @Test
    fun `rechaza un numero de barras no valido`() {
        assertThrows(IllegalArgumentException::class.java) {
            waveHeights(count = 0, phase = 0f, amplitude = 1f)
        }
    }
}
