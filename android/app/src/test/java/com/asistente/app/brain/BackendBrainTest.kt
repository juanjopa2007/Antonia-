package com.asistente.app.brain

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun user(text: String) = JSONObject().put("role", "user").put("content", text)

private fun assistant(text: String) = JSONObject().put("role", "assistant")
    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

private fun toolResultMessage() = JSONObject().put("role", "user")
    .put("content", JSONArray().put(JSONObject().put("type", "tool_result").put("tool_use_id", "t1").put("content", "12")))

private fun toolUseMessage() = JSONObject().put("role", "assistant")
    .put("content", JSONArray().put(JSONObject().put("type", "tool_use").put("id", "t1").put("name", "calculator")))

class ChatHistoryTest {

    @Test
    fun `un historial corto no se toca`() {
        val messages = listOf(user("a"), assistant("b"), user("c"))
        assertEquals(3, trimHistory(messages, 10).size)
    }

    @Test
    fun `descarta lo mas antiguo y empieza en un mensaje de usuario`() {
        val messages = listOf(user("1"), assistant("1"), user("2"), assistant("2"), user("3"))
        val trimmed = trimHistory(messages, 4)
        assertEquals(listOf("2", "2", "3").size, trimmed.size)
        assertEquals("2", trimmed.first().getString("content"))
    }

    @Test
    fun `nunca empieza en un resultado de herramienta ni separa herramienta y resultado`() {
        val messages = listOf(user("1"), toolUseMessage(), toolResultMessage(), assistant("fin"), user("2"))
        val trimmed = trimHistory(messages, 3)
        assertEquals(1, trimmed.size)
        assertEquals("2", trimmed.first().getString("content"))
    }

    @Test
    fun `siempre conserva el ultimo mensaje`() {
        assertEquals(1, trimHistory(listOf(assistant("x")), 1).size)
    }

    @Test
    fun `rechaza un maximo no valido`() {
        assertThrows(IllegalArgumentException::class.java) { trimHistory(listOf(user("a")), 0) }
    }
}

class BackendBrainTest {

    private class FakeBackend(var reply: (ChatRequest) -> ChatResult) : ChatBackend {
        val requests = mutableListOf<ChatRequest>()
        override suspend fun chat(request: ChatRequest): ChatResult {
            requests += request
            return reply(request)
        }
    }

    private fun answer(text: String) = ChatResult(text, listOf(assistant(text)))

    private fun brain(backend: ChatBackend, max: Int = 40) = BackendBrain(
        backend = backend,
        maxMessages = max,
        now = { "2026-11-23T13:28:00+01:00" },
        timezone = { "Europe/Madrid" },
    )

    @Test
    fun `la primera peticion lleva solo el mensaje del usuario y el contexto`() = runTest {
        val backend = FakeBackend { answer("Hola") }
        assertEquals("Hola", brain(backend).respond("hola"))

        val request = backend.requests.single()
        assertEquals(1, request.messages.size)
        assertEquals("hola", request.messages[0].getString("content"))

        val json = request.toJson()
        assertEquals(0, json.getJSONArray("clientTools").length())
        val context = json.getJSONObject("context")
        assertEquals("2026-11-23T13:28:00+01:00", context.getString("now"))
        assertEquals("Europe/Madrid", context.getString("timezone"))
        assertEquals("Antonia", context.getString("assistantName"))
    }

    @Test
    fun `las peticiones siguientes incluyen la conversacion anterior`() = runTest {
        val backend = FakeBackend { answer("respuesta") }
        val brain = brain(backend)
        brain.respond("uno")
        brain.respond("dos")

        val second = backend.requests[1].messages
        assertEquals(3, second.size)
        assertEquals(listOf("user", "assistant", "user"), second.map { it.getString("role") })
        assertEquals("dos", second[2].getString("content"))
    }

    @Test
    fun `si el servidor falla el intercambio no se guarda`() = runTest {
        var fail = true
        val backend = FakeBackend {
            if (fail) throw BrainException(BrainFailure.Offline) else answer("ok")
        }
        val brain = brain(backend)
        val failure = runCatching { brain.respond("uno") }.exceptionOrNull()
        assertTrue(failure is BrainException)

        fail = false
        brain.respond("dos")
        assertEquals(1, backend.requests.last().messages.size)
        assertEquals("dos", backend.requests.last().messages[0].getString("content"))
    }

    @Test
    fun `el historial se recorta y siempre empieza en un mensaje de usuario`() = runTest {
        val backend = FakeBackend { answer("r") }
        val brain = brain(backend, max = 4)
        repeat(6) { brain.respond("p$it") }

        for (request in backend.requests) {
            assertTrue(request.messages.size <= 4)
            assertEquals("user", request.messages.first().getString("role"))
            assertTrue(request.messages.first().opt("content") is String)
        }
        assertEquals("p5", backend.requests.last().messages.last().getString("content"))
    }

    @Test
    fun `se guardan tambien los mensajes de herramientas del servidor`() = runTest {
        var first = true
        val backend = FakeBackend {
            if (first) {
                first = false
                ChatResult("Son doce.", listOf(toolUseMessage(), toolResultMessage(), assistant("Son doce.")))
            } else {
                answer("de nada")
            }
        }
        val brain = brain(backend)
        brain.respond("cuánto es el 15% de 80")
        brain.respond("gracias")

        assertEquals(5, backend.requests[1].messages.size)
    }

    @Test
    fun `empezar de nuevo olvida la conversacion`() = runTest {
        val backend = FakeBackend { answer("r") }
        val brain = brain(backend)
        brain.respond("uno")
        brain.clearHistory()
        brain.respond("dos")
        assertEquals(1, backend.requests[1].messages.size)
    }
}
