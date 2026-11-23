package com.asistente.app.brain

import org.json.JSONObject

/** Un mensaje de usuario con texto plano (no el resultado de una herramienta). */
internal fun JSONObject.isPlainUserMessage(): Boolean =
    optString("role") == "user" && opt("content") is String

/**
 * Recorta el historial para no enviar conversaciones interminables.
 *
 * Siempre empieza en un mensaje de usuario con texto plano: así nunca se separa una petición
 * de herramienta de su resultado, algo que la API de Claude rechaza.
 */
fun trimHistory(messages: List<JSONObject>, maxMessages: Int): List<JSONObject> {
    require(maxMessages >= 1) { "maxMessages debe ser >= 1" }
    var start = 0
    while (start < messages.size - 1 &&
        (messages.size - start > maxMessages || !messages[start].isPlainUserMessage())
    ) {
        start++
    }
    return messages.subList(start, messages.size).toList()
}
