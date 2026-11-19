package com.asistente.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.voiceDataStore by preferencesDataStore(name = "voice_settings")

/** Guarda los ajustes de voz en el móvil, de forma persistente. */
class VoiceSettingsRepository(private val context: Context) {

    private val voiceKey = stringPreferencesKey("voice_id")
    private val rateKey = floatPreferencesKey("speech_rate")

    val settings: Flow<VoiceSettings> = context.voiceDataStore.data.map { prefs ->
        VoiceSettings(
            voiceId = prefs[voiceKey],
            rate = VoiceSettings.clampRate(prefs[rateKey] ?: VoiceSettings.DEFAULT_RATE),
        )
    }

    suspend fun current(): VoiceSettings = settings.first()

    suspend fun setRate(rate: Float) {
        context.voiceDataStore.edit { it[rateKey] = VoiceSettings.clampRate(rate) }
    }

    suspend fun setVoice(voiceId: String?) {
        context.voiceDataStore.edit {
            if (voiceId == null) it.remove(voiceKey) else it[voiceKey] = voiceId
        }
    }
}
