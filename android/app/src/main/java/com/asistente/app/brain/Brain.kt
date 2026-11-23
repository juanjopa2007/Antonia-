package com.asistente.app.brain

/** Quien genera la respuesta de Antonia a lo que dice el usuario. */
interface Brain {
    suspend fun respond(userText: String): String
}

enum class BrainFailure {
    /** Falta la dirección del servidor o el token en los ajustes. */
    NotConfigured,

    /** El servidor rechazó el token. */
    Unauthorized,

    /** No se pudo conectar con el servidor o se cortó la conexión. */
    Offline,

    /** El servidor o Claude devolvieron un error. */
    Server,

    Unknown,
}

class BrainException(
    val failure: BrainFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
