package com.asistente.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asistente.app.settings.VoiceSettings
import com.asistente.app.settings.VoiceSettingsRepository
import com.asistente.app.settings.toParams
import com.asistente.voice.AndroidSpeechToText
import com.asistente.voice.AndroidTextToSpeech
import com.asistente.voice.SpanishVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une la voz del móvil, los ajustes guardados y el controlador de la conversación. */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val stt = AndroidSpeechToText(application)
    private val tts = AndroidTextToSpeech(application)
    private val repository = VoiceSettingsRepository(application)

    private val controller = ConversationController(
        scope = viewModelScope,
        stt = stt,
        tts = tts,
        brain = OfflineBrain(),
        settings = repository::current,
    )

    val ui: StateFlow<ConversationUi> = controller.ui

    val settings: StateFlow<VoiceSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettings())

    private val _voices = MutableStateFlow<List<SpanishVoice>>(emptyList())
    val voices: StateFlow<List<SpanishVoice>> = _voices.asStateFlow()

    private val _voicesLoaded = MutableStateFlow(false)
    val voicesLoaded: StateFlow<Boolean> = _voicesLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            _voices.value = tts.voices()
            _voicesLoaded.value = true
        }
    }

    fun onOrbTapped() = controller.onOrbTapped()

    /** Se llama al salir de la pantalla principal para no dejar el micrófono ni la voz activos. */
    fun stopConversation() = controller.reset()

    fun setRate(rate: Float) {
        viewModelScope.launch { repository.setRate(rate) }
    }

    fun setVoice(voiceId: String?) {
        viewModelScope.launch { repository.setVoice(voiceId) }
    }

    /** Pronuncia una frase de prueba con los ajustes actuales. */
    fun testVoice() {
        tts.speak(TEST_PHRASE, settings.value.toParams()) { }
    }

    fun stopTest() = tts.stop()

    override fun onCleared() {
        stt.release()
        tts.release()
    }

    private companion object {
        const val TEST_PHRASE = "Hola, soy Antonia. Así es mi voz con estos ajustes."
    }
}
