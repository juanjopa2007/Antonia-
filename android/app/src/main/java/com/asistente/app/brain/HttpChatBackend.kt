package com.asistente.app.brain

import com.asistente.app.settings.ConnectionSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Comunicación real con el backend por HTTPS. */
class HttpChatBackend(
    private val settings: suspend () -> ConnectionSettings,
) : ChatBackend {

    override suspend fun chat(request: ChatRequest): ChatResult {
        val connection = open(settings(), "/v1/chat", "POST")
        return runCancellable(connection) {
            try {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "text/event-stream")
                connection.readTimeout = CHAT_READ_TIMEOUT_MS
                connection.doOutput = true
                connection.outputStream.use { it.write(request.toJson().toString().toByteArray(Charsets.UTF_8)) }
                checkStatus(connection.responseCode)
                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { parseChatStream(it) }
            } catch (e: IOException) {
                throw BrainException(BrainFailure.Offline, "No se pudo conectar con el servidor", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Comprueba la dirección y el token sin llamar a Claude. Lanza [BrainException] si algo falla. */
    suspend fun ping(candidate: ConnectionSettings) {
        val connection = open(candidate, "/v1/ping", "GET")
        runCancellable(connection) {
            try {
                checkStatus(connection.responseCode)
            } catch (e: IOException) {
                throw BrainException(BrainFailure.Offline, "No se pudo conectar con el servidor", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun open(connectionSettings: ConnectionSettings, path: String, method: String): HttpURLConnection {
        val base = connectionSettings.normalizedUrl()
        if (base == null || connectionSettings.token.isBlank()) {
            throw BrainException(BrainFailure.NotConfigured)
        }
        return (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer ${connectionSettings.token.trim()}")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = PING_READ_TIMEOUT_MS
        }
    }

    private fun checkStatus(code: Int) {
        when {
            code == 200 -> Unit
            code == 401 || code == 403 -> throw BrainException(BrainFailure.Unauthorized)
            code == 429 -> throw BrainException(BrainFailure.Server, "Demasiadas peticiones seguidas. Espera un momento.")
            else -> throw BrainException(BrainFailure.Server, "El servidor respondió con el error $code")
        }
    }

    /** Ejecuta la operación bloqueante fuera del hilo principal y la corta si se cancela. */
    private suspend fun <T> runCancellable(connection: HttpURLConnection, block: () -> T): T = coroutineScope {
        val work = async(Dispatchers.IO) { block() }
        try {
            work.await()
        } catch (e: CancellationException) {
            connection.disconnect()
            throw e
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val PING_READ_TIMEOUT_MS = 15_000

        // El servidor envía un latido cada 15 s, así que esta espera solo salta si la conexión muere.
        const val CHAT_READ_TIMEOUT_MS = 90_000
    }
}
