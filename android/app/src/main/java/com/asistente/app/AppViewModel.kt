package com.asistente.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asistente.app.brain.BackendBrain
import com.asistente.app.brain.BrainException
import com.asistente.app.brain.BrainFailure
import com.asistente.app.brain.HttpChatBackend
import com.asistente.app.settings.ConnectionSettings
import com.asistente.app.settings.ConnectionSettingsRepository
import com.asistente.app.settings.ConnectionTest
import com.asistente.app.settings.VoiceSettings
import com.asistente.app.settings.VoiceSettingsRepository
import com.asistente.app.settings.toParams
import com.asistente.voice.AndroidSpeechToText
import com.asistente.voice.AndroidTextToSpeech
import com.asistente.voice.SpanishVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une la voz del móvil, los ajustes guardados, el servidor y el controlador de la conversación. */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val stt = AndroidSpeechToText(application)
    private val tts = AndroidTextToSpeech(application)
    private val voiceRepository = VoiceSettingsRepository(application)
    private val connectionRepository = ConnectionSettingsRepository(application)

    private val backend = HttpChatBackend(connectionRepository::current)
    private val brain = BackendBrain(backend)

    private val controller = ConversationController(
        scope = viewModelScope,
        stt = stt,
        tts = tts,
        brain = brain,
        settings = voiceRepository::current,
    )

    val ui: StateFlow<ConversationUi> = controller.ui

    val settings: StateFlow<VoiceSettings> = voiceRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettings())

    val connection: StateFlow<ConnectionSettings> = connectionRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionSettings())

    private val _connectionTest = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val connectionTest: StateFlow<ConnectionTest> = _connectionTest.asStateFlow()

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

    /** Antonia olvida lo hablado hasta ahora. */
    fun newConversation() {
        brain.clearHistory()
        controller.reset()
    }

    fun setRate(rate: Float) {
        viewModelScope.launch { voiceRepository.setRate(rate) }
    }

    fun setVoice(voiceId: String?) {
        viewModelScope.launch { voiceRepository.setVoice(voiceId) }
    }

    /** Pronuncia una frase de prueba con los ajustes actuales. */
    fun testVoice() {
        tts.speak(TEST_PHRASE, settings.value.toParams()) { }
    }

    fun stopTest() = tts.stop()

    fun saveConnection(serverUrl: String, token: String) {
        viewModelScope.launch { connectionRepository.save(serverUrl, token) }
    }

    /** Comprueba la dirección y el token escritos, estén guardados o no. */
    fun testConnection(serverUrl: String, token: String) {
        viewModelScope.launch {
            _connectionTest.value = ConnectionTest.Testing
            _connectionTest.value = try {
                backend.ping(ConnectionSettings(serverUrl, token))
                ConnectionTest.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: BrainException) {
                ConnectionTest.Failed(e.failure)
            } catch (e: Exception) {
                ConnectionTest.Failed(BrainFailure.Unknown)
            }
        }
    }

    fun clearConnectionTest() {
        _connectionTest.value = ConnectionTest.Idle
    }

    override fun onCleared() {
        stt.release()
        tts.release()
    }

    private companion object {
        const val TEST_PHRASE = "Hola, soy Antonia. Así es mi voz con estos ajustes."
    }
}
