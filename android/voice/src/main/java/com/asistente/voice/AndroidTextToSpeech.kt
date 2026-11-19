package com.asistente.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Síntesis de voz con el motor de texto a voz del sistema. Debe usarse desde el hilo principal. */
class AndroidTextToSpeech(context: Context) : TextToSpeechEngine {

    private class Session(
        val ids: Set<String>,
        val finalId: String,
        val onFinished: (SpeakOutcome) -> Unit,
    )

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val waiting = mutableListOf<(Boolean) -> Unit>()
    private var session: Session? = null

    init {
        // El callback de inicio se reenvía al hilo principal, así que `engine` ya estará asignado.
        engine = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }
    }

    private fun onInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            flushWaiting(false)
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(progress)
        // Si falta el idioma, se sigue adelante: la voz elegida por el usuario puede cubrirlo.
        tts.setLanguage(SPANISH)
        ready = true
        flushWaiting(true)
    }

    private fun whenReady(block: (Boolean) -> Unit) {
        when {
            ready -> block(true)
            failed -> block(false)
            else -> waiting += block
        }
    }

    private fun flushWaiting(ok: Boolean) {
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(ok) }
    }

    override suspend fun voices(): List<SpanishVoice> = suspendCoroutine { continuation ->
        whenReady { ok -> continuation.resume(if (ok) loadVoices() else emptyList()) }
    }

    private fun loadVoices(): List<SpanishVoice> {
        val all: Set<Voice> = try {
            engine?.voices.orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        val sorted = all
            .filter { it.locale.language == "es" }
            .sortedWith(compareBy<Voice>({ it.locale.country != "ES" }, { -it.quality }, { it.name }))

        val counters = HashMap<String, Int>()
        return sorted.map { voice ->
            val region = voice.locale.getDisplayName(Locale.forLanguageTag("es"))
                .replaceFirstChar { it.uppercase() }
            val connection = if (voice.isNetworkConnectionRequired) "en línea" else "sin conexión"
            val base = "$region · ${qualityLabel(voice.quality)} · $connection"
            val n = (counters[base] ?: 0) + 1
            counters[base] = n
            SpanishVoice(id = voice.name, label = if (n > 1) "$base ($n)" else base)
        }
    }

    private fun qualityLabel(quality: Int): String = when {
        quality >= Voice.QUALITY_VERY_HIGH -> "calidad muy alta"
        quality >= Voice.QUALITY_HIGH -> "calidad alta"
        quality >= Voice.QUALITY_NORMAL -> "calidad normal"
        else -> "calidad baja"
    }

    override fun speak(text: String, params: SpeechParams, onFinished: (SpeakOutcome) -> Unit) {
        stop()
        val chunks = splitForSpeech(text, MAX_CHUNK_LENGTH)
        if (chunks.isEmpty()) {
            onFinished(SpeakOutcome.Completed)
            return
        }
        val base = UUID.randomUUID().toString()
        val ids = chunks.indices.map { "$base-$it" }
        val mine = Session(ids.toSet(), ids.last(), onFinished)
        session = mine

        whenReady { ok ->
            // Si mientras tanto se interrumpió o se pidió otra locución, esta ya no cuenta.
            if (session !== mine) return@whenReady
            val tts = engine
            if (!ok || tts == null) {
                finish(mine, SpeakOutcome.Failed)
                return@whenReady
            }
            applyParams(tts, params)
            for (i in chunks.indices) {
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (tts.speak(chunks[i], mode, null, ids[i]) == TextToSpeech.ERROR) {
                    tts.stop()
                    finish(mine, SpeakOutcome.Failed)
                    return@whenReady
                }
            }
        }
    }

    private fun applyParams(tts: TextToSpeech, params: SpeechParams) {
        val chosen: Voice? = params.voiceId?.let { id ->
            try {
                tts.voices?.firstOrNull { it.name == id }
            } catch (e: Exception) {
                null
            }
        }
        if (chosen != null) tts.voice = chosen else tts.setLanguage(SPANISH)
        tts.setSpeechRate(params.rate.coerceIn(MIN_RATE, MAX_RATE))
    }

    private fun finish(target: Session, outcome: SpeakOutcome) {
        if (session !== target) return
        session = null
        target.onFinished(outcome)
    }

    override fun stop() {
        session = null
        engine?.stop()
    }

    override fun release() {
        session = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        failed = true
        flushWaiting(false)
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            main.post {
                val current = session ?: return@post
                if (utteranceId == current.finalId) finish(current, SpeakOutcome.Completed)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            onError(utteranceId, -1)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post {
                val current = session ?: return@post
                if (utteranceId != null && utteranceId in current.ids) {
                    engine?.stop()
                    finish(current, SpeakOutcome.Failed)
                }
            }
        }
    }

    private companion object {
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
        const val MAX_CHUNK_LENGTH = 1000
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
    }
}
