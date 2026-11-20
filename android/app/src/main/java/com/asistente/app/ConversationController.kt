package com.asistente.app

import com.asistente.app.settings.VoiceSettings
import com.asistente.app.settings.toParams
import com.asistente.core.ui.OrbState
import com.asistente.voice.SpeakOutcome
import com.asistente.voice.SpeechToText
import com.asistente.voice.SttError
import com.asistente.voice.SttEvent
import com.asistente.voice.TextToSpeechEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Avisos que la pantalla traduce a texto para el usuario. */
enum class ConversationMessage {
    NoSpeechHeard,
    RecognizerBusy,
    NeedsPermission,
    NetworkProblem,
    RecognizerUnavailable,
    SpeechFailed,
    BrainFailed,
    Generic,
}

data class ConversationUi(
    val orb: OrbState = OrbState.Idle,
    /** Lo que el usuario está diciendo o ha dicho. */
    val transcript: String = "",
    /** Lo que Antonia responde. */
    val reply: String = "",
    /** Volumen del micrófono, entre 0 y 1. */
    val level: Float = 0f,
    val message: ConversationMessage? = null,
)

/**
 * Coordina escuchar, pensar y hablar. El micrófono solo se abre cuando el usuario toca el botón.
 * Todo ocurre en el hilo principal.
 */
class ConversationController(
    private val scope: CoroutineScope,
    private val stt: SpeechToText,
    private val tts: TextToSpeechEngine,
    private val brain: Brain,
    private val settings: suspend () -> VoiceSettings,
) {
    private val _ui = MutableStateFlow(ConversationUi())
    val ui: StateFlow<ConversationUi> = _ui.asStateFlow()

    /** Tarea que obtiene y pronuncia la respuesta. */
    private var job: Job? = null

    /** Verdadero desde que se abre el micrófono hasta que llega el texto final o un fallo. */
    private var awaitingText = false

    fun onOrbTapped() {
        when (_ui.value.orb) {
            // Empezar a escuchar.
            OrbState.Idle -> startListening()
            // Terminar de hablar: se pide lo entendido y se pasa a esperar la respuesta.
            OrbState.Listening -> {
                _ui.update { it.copy(orb = OrbState.Thinking, level = 0f) }
                stt.stop()
            }
            // Cancelar la petición en curso.
            OrbState.Thinking -> reset()
            // Interrumpir la respuesta hablada y escuchar de nuevo.
            OrbState.Speaking -> {
                tts.stop()
                startListening()
            }
        }
    }

    /** Detiene todo y vuelve a reposo. */
    fun reset() {
        job?.cancel()
        job = null
        awaitingText = false
        stt.cancel()
        tts.stop()
        _ui.value = ConversationUi()
    }

    private fun startListening() {
        job?.cancel()
        job = null
        if (!stt.isAvailable) {
            fail(ConversationMessage.RecognizerUnavailable)
            return
        }
        awaitingText = true
        _ui.value = ConversationUi(orb = OrbState.Listening)
        stt.start(::onSttEvent)
    }

    private fun onSttEvent(event: SttEvent) {
        when (event) {
            SttEvent.Ready -> Unit
            is SttEvent.Level -> if (_ui.value.orb == OrbState.Listening) {
                _ui.update { it.copy(level = event.value) }
            }
            is SttEvent.Partial -> if (_ui.value.orb == OrbState.Listening) {
                _ui.update { it.copy(transcript = event.text) }
            }
            is SttEvent.Final -> onFinalText(event.text)
            is SttEvent.Failed -> onSttFailed(event.error)
        }
    }

    private fun waitingForText(): Boolean {
        val orb = _ui.value.orb
        return awaitingText && (orb == OrbState.Listening || orb == OrbState.Thinking)
    }

    private fun onFinalText(text: String) {
        if (!waitingForText()) return
        awaitingText = false
        val clean = text.trim()
        if (clean.isEmpty()) {
            fail(ConversationMessage.NoSpeechHeard)
            return
        }
        _ui.update { it.copy(orb = OrbState.Thinking, transcript = clean, reply = "", level = 0f) }
        job = scope.launch { respondAndSpeak(clean) }
    }

    private fun onSttFailed(error: SttError) {
        if (!waitingForText()) return
        fail(
            when (error) {
                SttError.NoSpeech -> ConversationMessage.NoSpeechHeard
                SttError.Busy -> ConversationMessage.RecognizerBusy
                SttError.NoPermission -> ConversationMessage.NeedsPermission
                SttError.Network -> ConversationMessage.NetworkProblem
                SttError.Unavailable -> ConversationMessage.RecognizerUnavailable
                SttError.Other -> ConversationMessage.Generic
            },
        )
    }

    private suspend fun respondAndSpeak(userText: String) {
        val reply = try {
            brain.respond(userText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(ConversationMessage.BrainFailed)
            return
        }
        val params = settings().toParams()
        _ui.update { it.copy(orb = OrbState.Speaking, reply = reply) }
        tts.speak(reply, params, ::onSpeakFinished)
    }

    private fun onSpeakFinished(outcome: SpeakOutcome) {
        if (_ui.value.orb != OrbState.Speaking) return
        when (outcome) {
            SpeakOutcome.Completed -> {
                job = null
                _ui.update { it.copy(orb = OrbState.Idle, level = 0f) }
            }
            SpeakOutcome.Failed -> fail(ConversationMessage.SpeechFailed, keepText = true)
        }
    }

    private fun fail(message: ConversationMessage, keepText: Boolean = false) {
        job = null
        awaitingText = false
        stt.cancel()
        _ui.update { current ->
            if (keepText) {
                current.copy(orb = OrbState.Idle, level = 0f, message = message)
            } else {
                ConversationUi(message = message)
            }
        }
    }
}
