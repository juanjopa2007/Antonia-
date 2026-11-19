package com.asistente.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeechChunksTest {

    @Test
    fun `un texto vacio no produce trozos`() {
        assertTrue(splitForSpeech("", 100).isEmpty())
        assertTrue(splitForSpeech("   \n ", 100).isEmpty())
    }

    @Test
    fun `un texto corto se devuelve entero`() {
        assertEquals(listOf("Hola, soy Antonia."), splitForSpeech("  Hola, soy Antonia.  ", 100))
    }

    @Test
    fun `corta entre frases y ningun trozo supera el maximo`() {
        val text = "Primera frase corta. Segunda frase un poco mas larga. Tercera. ¿Cuarta frase final?"
        val chunks = splitForSpeech(text, 40)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 40 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun `parte una frase mas larga que el maximo por palabras`() {
        val text = (1..30).joinToString(" ") { "palabra$it" }
        val chunks = splitForSpeech(text, 50)
        assertTrue(chunks.all { it.length <= 50 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun `parte una palabra sin espacios mas larga que el maximo`() {
        val chunks = splitForSpeech("a".repeat(25), 10)
        assertEquals(listOf(10, 10, 5), chunks.map { it.length })
    }

    @Test
    fun `rechaza un maximo no valido`() {
        assertThrows(IllegalArgumentException::class.java) { splitForSpeech("hola", 0) }
    }
}
