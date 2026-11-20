package com.asistente.app

import com.asistente.app.settings.VoiceSettings
import com.asistente.core.ui.OrbState
import com.asistente.voice.SpanishVoice
import com.asistente.voice.SpeakOutcome
import com.asistente.voice.SpeechParams
import com.asistente.voice.SpeechToText
import com.asistente.voice.SttError
import com.asistente.voice.SttEvent
import com.asistente.voice.TextToSpeechEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStt(var available: Boolean = true) : SpeechToText {
    var starts = 0
    var stops = 0
    var cancels = 0
    private var listener: ((SttEvent) -> Unit)? = null

    override val isAvailable: Boolean get() = available
    override fun start(listener: (SttEvent) -> Unit) {
        starts++
        this.listener = listener
    }
    override fun stop() { stops++ }
    override fun cancel() { cancels++ }
    override fun release() = Unit

    fun emit(event: SttEvent) = listener!!.invoke(event)
}

private class FakeTts : TextToSpeechEngine {
    val spoken = mutableListOf<Pair<String, SpeechParams>>()
    var stops = 0
    private var onFinished: ((SpeakOutcome) -> Unit)? = null

    override suspend fun voices(): List<SpanishVoice> = emptyList()
    override fun speak(text: String, params: SpeechParams, onFinished: (SpeakOutcome) -> Unit) {
        spoken += text to params
        this.onFinished = onFinished
    }
    override fun stop() { stops++ }
    override fun release() = Unit

    fun finish(outcome: SpeakOutcome) = onFinished!!.invoke(outcome)
}

private class FakeBrain(private val answer: suspend (String) -> String) : Brain {
    val questions = mutableListOf<String>()
    override suspend fun respond(userText: String): String {
        questions += userText
        return answer(userText)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationControllerTest {

    private class Setup(
        scope: TestScope,
        val stt: FakeStt = FakeStt(),
        val tts: FakeTts = FakeTts(),
        val brain: FakeBrain = FakeBrain { "Respuesta a $it" },
        settings: VoiceSettings = VoiceSettings(voiceId = "voz-1", rate = 1.4f),
    ) {
        // Ámbito propio sobre el planificador del test: advanceUntilIdle() ejecuta sus tareas.
        private val controllerScope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + SupervisorJob())
        val controller = ConversationController(controllerScope, stt, tts, brain) { settings }
        val ui get() = controller.ui.value
    }

    /** Deja a Antonia hablando una respuesta. */
    private fun TestScope.speaking(setup: Setup) {
        setup.controller.onOrbTapped()
        setup.stt.emit(SttEvent.Final("hola"))
        advanceUntilIdle()
        assertEquals(OrbState.Speaking, setup.ui.orb)
    }

    @Test
    fun `tocar en reposo abre el microfono y pasa a escuchar`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        assertEquals(OrbState.Listening, s.ui.orb)
        assertEquals(1, s.stt.starts)
    }

    @Test
    fun `el texto parcial y el volumen se reflejan mientras escucha`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Partial("qué tengo"))
        s.stt.emit(SttEvent.Level(0.6f))
        assertEquals("qué tengo", s.ui.transcript)
        assertEquals(0.6f, s.ui.level, 0f)
    }

    @Test
    fun `el texto final llega al cerebro y la respuesta se pronuncia con los ajustes`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Final("  qué hora es  "))
        assertEquals(OrbState.Thinking, s.ui.orb)
        advanceUntilIdle()

        assertEquals(listOf("qué hora es"), s.brain.questions)
        assertEquals(OrbState.Speaking, s.ui.orb)
        assertEquals("Respuesta a qué hora es", s.ui.reply)
        assertEquals(1, s.tts.spoken.size)
        assertEquals("Respuesta a qué hora es", s.tts.spoken[0].first)
        assertEquals(SpeechParams(voiceId = "voz-1", rate = 1.4f), s.tts.spoken[0].second)
    }

    @Test
    fun `al terminar de hablar vuelve a reposo y conserva el texto`() = runTest {
        val s = Setup(this)
        speaking(s)
        s.tts.finish(SpeakOutcome.Completed)
        assertEquals(OrbState.Idle, s.ui.orb)
        assertEquals("hola", s.ui.transcript)
        assertNull(s.ui.message)
    }

    @Test
    fun `tocar mientras habla la interrumpe y vuelve a escuchar`() = runTest {
        val s = Setup(this)
        speaking(s)
        s.controller.onOrbTapped()
        assertEquals(1, s.tts.stops)
        assertEquals(OrbState.Listening, s.ui.orb)
        assertEquals(2, s.stt.starts)
        assertEquals("", s.ui.reply)
    }

    @Test
    fun `tocar mientras escucha pide el resultado y pasa a pensar`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.controller.onOrbTapped()
        assertEquals(1, s.stt.stops)
        assertEquals(OrbState.Thinking, s.ui.orb)

        s.stt.emit(SttEvent.Final("hola"))
        advanceUntilIdle()
        assertEquals(OrbState.Speaking, s.ui.orb)
    }

    @Test
    fun `si no se entiende nada vuelve a reposo con un aviso`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Failed(SttError.NoSpeech))
        assertEquals(OrbState.Idle, s.ui.orb)
        assertEquals(ConversationMessage.NoSpeechHeard, s.ui.message)
        assertTrue(s.brain.questions.isEmpty())
    }

    @Test
    fun `un texto final vacio se trata como que no se oyo nada`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Final("   "))
        assertEquals(OrbState.Idle, s.ui.orb)
        assertEquals(ConversationMessage.NoSpeechHeard, s.ui.message)
    }

    @Test
    fun `tocar mientras piensa cancela y una respuesta tardia no se pronuncia`() = runTest {
        val gate = CompletableDeferred<String>()
        val s = Setup(this, brain = FakeBrain { gate.await() })
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Final("hola"))
        advanceUntilIdle()
        assertEquals(OrbState.Thinking, s.ui.orb)

        s.controller.onOrbTapped()
        assertEquals(OrbState.Idle, s.ui.orb)

        gate.complete("demasiado tarde")
        advanceUntilIdle()
        assertTrue(s.tts.spoken.isEmpty())
        assertEquals(OrbState.Idle, s.ui.orb)
    }

    @Test
    fun `si el cerebro falla vuelve a reposo con un aviso`() = runTest {
        val s = Setup(this, brain = FakeBrain { error("fallo") })
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Final("hola"))
        advanceUntilIdle()
        assertEquals(OrbState.Idle, s.ui.orb)
        assertEquals(ConversationMessage.BrainFailed, s.ui.message)
        assertTrue(s.tts.spoken.isEmpty())
    }

    @Test
    fun `si la voz falla avisa pero conserva lo dicho`() = runTest {
        val s = Setup(this)
        speaking(s)
        s.tts.finish(SpeakOutcome.Failed)
        assertEquals(OrbState.Idle, s.ui.orb)
        assertEquals(ConversationMessage.SpeechFailed, s.ui.message)
        assertEquals("Respuesta a hola", s.ui.reply)
    }

    @Test
    fun `sin servicio de reconocimiento no se abre el microfono`() = runTest {
        val s = Setup(this, stt = FakeStt(available = false))
        s.controller.onOrbTapped()
        assertEquals(OrbState.Idle, s.ui.orb)
        assertEquals(ConversationMessage.RecognizerUnavailable, s.ui.message)
        assertEquals(0, s.stt.starts)
    }

    @Test
    fun `la falta de permiso se comunica`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.stt.emit(SttEvent.Failed(SttError.NoPermission))
        assertEquals(ConversationMessage.NeedsPermission, s.ui.message)
        assertEquals(OrbState.Idle, s.ui.orb)
    }

    @Test
    fun `un resultado que llega en reposo se ignora`() = runTest {
        val s = Setup(this)
        s.controller.onOrbTapped()
        s.controller.reset()
        s.stt.emit(SttEvent.Final("tarde"))
        advanceUntilIdle()
        assertEquals(OrbState.Idle, s.ui.orb)
        assertTrue(s.brain.questions.isEmpty())
    }

    @Test
    fun `reset detiene escucha y voz`() = runTest {
        val s = Setup(this)
        speaking(s)
        s.controller.reset()
        assertEquals(OrbState.Idle, s.ui.orb)
        assertTrue(s.tts.stops >= 1)
        assertTrue(s.stt.cancels >= 1)
        assertFalse(s.ui.reply.isNotEmpty())
    }

    @Test
    fun `una locucion terminada tras interrumpir no cambia el estado`() = runTest {
        val s = Setup(this)
        speaking(s)
        s.controller.onOrbTapped() // interrumpe y escucha
        s.tts.finish(SpeakOutcome.Completed) // aviso tardío del motor
        assertEquals(OrbState.Listening, s.ui.orb)
    }
}
