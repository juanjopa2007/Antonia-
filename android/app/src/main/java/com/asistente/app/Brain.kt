package com.asistente.app

/** Quien genera la respuesta de Antonia a lo que dice el usuario. */
interface Brain {
    suspend fun respond(userText: String): String
}

/**
 * Cerebro de la entrega de voz: todavía no hay conexión con el servidor de Claude, así que
 * Antonia lo dice con claridad y repite lo que ha entendido. Sirve para comprobar el recorrido
 * completo de la voz (micrófono, reconocimiento, respuesta hablada) antes de conectar a Claude.
 */
class OfflineBrain : Brain {
    override suspend fun respond(userText: String): String =
        "Todavía no estoy conectada a mi cerebro en internet, pero te he entendido. Has dicho: $userText"
}
