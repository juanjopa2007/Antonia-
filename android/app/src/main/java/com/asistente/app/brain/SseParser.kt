package com.asistente.app.brain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class SseEvent(val name: String, val data: String)

/**
 * Lector del formato Server-Sent Events. Se le van pasando las líneas una a una (sin salto de
 * línea) y devuelve un evento cuando encuentra la línea en blanco que lo cierra.
 */
class SseParser {
    private var name = ""
    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) {
            if (name.isEmpty() && !hasData) return null
            val event = SseEvent(name.ifEmpty { "message" }, data.toString())
            name = ""
            data.clear()
            hasData = false
            return event
        }
        // Las líneas que empiezan por «:» son comentarios (el servidor las usa como latido).
        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        when (field) {
            "event" -> name = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }
}

/** Resultado completo de una petición de chat. */
data class ChatResult(
    /** Texto que Antonia debe pronunciar. */
    val text: String,
    /** Mensajes nuevos que hay que añadir al historial (incluye herramientas del servidor). */
    val newMessages: List<JSONObject>,
)

/**
 * Interpreta la respuesta en streaming de `POST /v1/chat`.
 * Lanza [BrainException] si el servidor informa de un error o la respuesta se corta.
 */
fun parseChatStream(lines: Sequence<String>): ChatResult {
    val parser = SseParser()
    val text = StringBuilder()
    var done: JSONObject? = null

    for (line in lines) {
        val event = parser.feed(line) ?: continue
        val json = try {
            JSONObject(event.data)
        } catch (e: JSONException) {
            throw BrainException(BrainFailure.Server, "Respuesta del servidor ilegible", e)
        }
        when (event.name) {
            "text" -> text.append(json.optString("delta"))
            // Entre el texto previo a una herramienta y la respuesta final hace falta un espacio.
            "tool_status" -> if (json.optString("status") == "running" &&
                text.isNotEmpty() && !text.last().isWhitespace()
            ) {
                text.append(' ')
            }
            "client_tool_calls" ->
                throw BrainException(BrainFailure.Server, "El servidor pidió una herramienta del teléfono que aún no existe")
            "error" ->
                throw BrainException(BrainFailure.Server, json.optString("message").ifEmpty { null })
            "done" -> {
                done = json
                break
            }
        }
    }

    val finished = done ?: throw BrainException(BrainFailure.Offline, "La conexión se cortó antes de terminar la respuesta")
    val answer = text.toString().trim()
    if (answer.isEmpty()) throw BrainException(BrainFailure.Server, "El servidor devolvió una respuesta vacía")

    val array: JSONArray = finished.optJSONArray("newMessages") ?: JSONArray()
    val messages = (0 until array.length()).map { array.getJSONObject(it) }
    return ChatResult(answer, messages)
}
