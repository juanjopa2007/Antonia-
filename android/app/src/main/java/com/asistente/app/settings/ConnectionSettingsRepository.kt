package com.asistente.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.connectionDataStore by preferencesDataStore(name = "connection_settings")

/**
 * Guarda la dirección y el token en el almacenamiento privado de la app. Ninguna otra app puede
 * leerlo; no se incluye en copias de seguridad porque `allowBackup` está desactivado.
 */
class ConnectionSettingsRepository(private val context: Context) {

    private val urlKey = stringPreferencesKey("server_url")
    private val tokenKey = stringPreferencesKey("token")

    val settings: Flow<ConnectionSettings> = context.connectionDataStore.data.map { prefs ->
        ConnectionSettings(serverUrl = prefs[urlKey].orEmpty(), token = prefs[tokenKey].orEmpty())
    }

    suspend fun current(): ConnectionSettings = settings.first()

    suspend fun save(serverUrl: String, token: String) {
        context.connectionDataStore.edit {
            it[urlKey] = serverUrl.trim()
            it[tokenKey] = token.trim()
        }
    }
}
