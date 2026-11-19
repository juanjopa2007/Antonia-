package com.asistente.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reconocimiento con el servicio de voz del sistema. El micrófono solo está abierto mientras
 * dura una sesión iniciada con [start]; nunca queda escuchando de fondo.
 * Debe usarse siempre desde el hilo principal.
 */
class AndroidSpeechToText(
    private val context: Context,
    private val languageTag: String = "es-ES",
) : SpeechToText {

    private var recognizer: SpeechRecognizer? = null
    private var listener: ((SttEvent) -> Unit)? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(listener: (SttEvent) -> Unit) {
        cancel()
        this.listener = listener
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(callbacks)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        engine.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        listener = null
        recognizer?.cancel()
    }

    override fun release() {
        listener = null
        recognizer?.destroy()
        recognizer = null
    }

    private fun emit(event: SttEvent) {
        listener?.invoke(event)
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = emit(SttEvent.Ready)

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // El valor suele moverse entre -2 y 10 dB.
            emit(SttEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val mapped = when (error) {
                // Lo provoca nuestro propio cancel(); no es un fallo real.
                SpeechRecognizer.ERROR_CLIENT -> return
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttError.NoSpeech
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttError.Busy
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttError.NoPermission
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SttError.Network
                else -> SttError.Other
            }
            emit(SttEvent.Failed(mapped))
        }

        override fun onResults(results: Bundle?) {
            emit(SttEvent.Final(results.firstResult().orEmpty()))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.firstResult()
            if (!text.isNullOrBlank()) emit(SttEvent.Partial(text))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
