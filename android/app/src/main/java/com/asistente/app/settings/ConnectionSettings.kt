package com.asistente.app.settings

import java.net.URI

/**
 * Dónde está el servidor y con qué token se accede a él. Los introduce el usuario en Ajustes;
 * nunca van escritos en el código.
 */
data class ConnectionSettings(
    val serverUrl: String = "",
    val token: String = "",
) {
    /**
     * Dirección limpia (sin barra final) o nula si no es válida. Solo se admite HTTPS: el token
     * viaja en cada petición y no debe enviarse sin cifrar.
     */
    fun normalizedUrl(): String? {
        val trimmed = serverUrl.trim().trimEnd('/')
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return null
        }
        if (uri.host.isNullOrEmpty()) return null
        return trimmed
    }

    val isConfigured: Boolean
        get() = normalizedUrl() != null && token.isNotBlank()
}
