package com.asistente.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SseParserTest {

    private fun parse(vararg lines: String): List<SseEvent> {
        val parser = SseParser()
        return lines.mapNotNull { parser.feed(it) }
    }

    @Test
    fun `un evento termina con una linea en blanco`() {
        val events = parse("event: text", "data: {\"a\":1}", "")
        assertEquals(listOf(SseEvent("text", "{\"a\":1}")), events)
    }

    @Test
    fun `sin linea en blanco el evento no se entrega`() {
        assertEquals(emptyList<SseEvent>(), parse("event: text", "data: hola"))
    }

    @Test
    fun `varios eventos seguidos`() {
        val events = parse("event: a", "data: 1", "", "event: b", "data: 2", "")
        assertEquals(listOf(SseEvent("a", "1"), SseEvent("b", "2")), events)
    }

    @Test
    fun `los comentarios de latido se ignoran`() {
        val events = parse(": ping", "", "event: a", "data: 1", ": ping", "")
        assertEquals(listOf(SseEvent("a", "1")), events)
    }

    @Test
    fun `varias lineas de datos se unen con salto de linea`() {
        assertEquals(listOf(SseEvent("a", "uno\ndos")), parse("event: a", "data: uno", "data: dos", ""))
    }

    @Test
    fun `sin nombre el evento se llama message y el espacio tras los dos puntos es opcional`() {
        assertEquals(listOf(SseEvent("message", "x")), parse("data:x", ""))
    }

    @Test
    fun `una linea en blanco suelta no produce eventos`() {
        assertNull(SseParser().feed(""))
    }
}

class ChatStreamTest {

    private fun stream(vararg events: Pair<String, String>): Sequence<String> =
        events.flatMap { (name, json) -> listOf("event: $name", "data: $json", "") }.asSequence()

    private val done =
        "done" to """{"type":"done","stopReason":"end_turn","usage":{"inputTokens":10,"outputTokens":5},""" +
            """"newMessages":[{"role":"assistant","content":[{"type":"text","text":"Son doce."}]}]}"""

    @Test
    fun `junta el texto y devuelve los mensajes nuevos`() {
        val result = parseChatStream(
            stream(
                "text" to """{"type":"text","delta":"Son "}""",
                "text" to """{"type":"text","delta":"doce."}""",
                done,
            ),
        )
        assertEquals("Son doce.", result.text)
        assertEquals(1, result.newMessages.size)
        assertEquals("assistant", result.newMessages[0].getString("role"))
    }

    @Test
    fun `separa con un espacio el texto anterior y posterior a una herramienta`() {
        val result = parseChatStream(
            stream(
                "text" to """{"type":"text","delta":"Voy a calcular."}""",
                "tool_status" to """{"type":"tool_status","id":"t1","name":"calculator","status":"running"}""",
                "tool_status" to """{"type":"tool_status","id":"t1","name":"calculator","status":"done"}""",
                "text" to """{"type":"text","delta":"Son doce."}""",
                done,
            ),
        )
        assertEquals("Voy a calcular. Son doce.", result.text)
    }

    @Test
    fun `ignora los latidos entre eventos`() {
        val lines = sequenceOf(": ping", "") + stream("text" to """{"type":"text","delta":"Hola"}""", done)
        assertEquals("Hola", parseChatStream(lines).text)
    }

    @Test
    fun `un evento de error del servidor lanza una excepcion con su mensaje`() {
        val e = assertThrows(BrainException::class.java) {
            parseChatStream(
                stream("error" to """{"type":"error","code":"upstream_unavailable","message":"Claude no está disponible."}"""),
            )
        }
        assertEquals(BrainFailure.Server, e.failure)
        assertEquals("Claude no está disponible.", e.message)
    }

    @Test
    fun `si la conexion se corta antes de done se considera sin conexion`() {
        val e = assertThrows(BrainException::class.java) {
            parseChatStream(stream("text" to """{"type":"text","delta":"Hola"}"""))
        }
        assertEquals(BrainFailure.Offline, e.failure)
    }

    @Test
    fun `una respuesta sin texto es un error`() {
        val e = assertThrows(BrainException::class.java) { parseChatStream(stream(done)) }
        assertEquals(BrainFailure.Server, e.failure)
    }

    @Test
    fun `una peticion de herramienta del telefono se rechaza mientras no existan`() {
        val e = assertThrows(BrainException::class.java) {
            parseChatStream(stream("client_tool_calls" to """{"type":"client_tool_calls","calls":[]}"""))
        }
        assertEquals(BrainFailure.Server, e.failure)
    }

    @Test
    fun `datos que no son JSON se tratan como error del servidor`() {
        val e = assertThrows(BrainException::class.java) {
            parseChatStream(sequenceOf("event: text", "data: no es json", ""))
        }
        assertEquals(BrainFailure.Server, e.failure)
    }
}
