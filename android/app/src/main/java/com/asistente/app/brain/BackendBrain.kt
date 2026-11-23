package com.asistente.app.brain

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId

data class ChatRequest(
    val messages: List<JSONObject>,
    val now: String,
    val timezone: String,
    val assistantName: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("messages", JSONArray(messages))
        .put("clientTools", JSONArray())
        .put(
            "context",
            JSONObject()
                .put("now", now)
                .put("timezone", timezone)
                .put("assistantName", assistantName),
        )
}

/** Canal de comunicación con el servidor. Separado para poder probar el historial sin red. */
interface ChatBackend {
    suspend fun chat(request: ChatRequest): ChatResult
}

/**
 * Cerebro conectado al backend seguro de Claude. Mantiene la conversación en memoria mientras
 * la app está abierta; la memoria permanente llegará en otra etapa.
 */
class BackendBrain(
    private val backend: ChatBackend,
    private val assistantName: String = "Antonia",
    private val maxMessages: Int = 40,
    private val now: () -> String = { OffsetDateTime.now().toString() },
    private val timezone: () -> String = { ZoneId.systemDefault().id },
) : Brain {

    private var history: List<JSONObject> = emptyList()

    override suspend fun respond(userText: String): String {
        val userMessage = JSONObject().put("role", "user").put("content", userText)
        val outgoing = trimHistory(history + userMessage, maxMessages)

        val result = backend.chat(ChatRequest(outgoing, now(), timezone(), assistantName))

        // Solo se guarda el intercambio si el servidor respondió bien.
        history = trimHistory(outgoing + result.newMessages, maxMessages)
        return result.text
    }

    /** Olvida la conversación actual. */
    fun clearHistory() {
        history = emptyList()
    }
}
