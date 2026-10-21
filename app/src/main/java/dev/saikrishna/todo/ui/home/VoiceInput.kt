package dev.saikrishna.todo.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Voice capture for the composer on Android's SpeechRecognizer.
 *
 * - Prefers the on-device recognizer (`createOnDeviceSpeechRecognizer`, API 31+) when the phone
 *   reports one, else the default recognition service. If the on-device one fails to start for
 *   language/availability reasons, retries once with the default service.
 * - Partial results stream into [onText] (the composer field), so the NL parser and its chips
 *   update live; the final result lands the same way and the user confirms with send.
 * - [level] (0..1) is the input level from onRmsChanged; the UI smooths and draws it.
 * Must be used on the main thread (SpeechRecognizer requirement).
 */
@Stable
class VoiceInput(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var level by mutableFloatStateOf(0f)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var triedFallback = false
    private var onText: (String) -> Unit = {}
    private var onDone: (error: String?) -> Unit = {}

    val available: Boolean
        get() = onDeviceAvailable() || SpeechRecognizer.isRecognitionAvailable(context)

    private fun onDeviceAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    /** Starts listening. [onText] receives the transcript so far; [onDone] gets null or a short message. */
    fun start(onText: (String) -> Unit, onDone: (error: String?) -> Unit) {
        if (listening) return
        this.onText = onText
        this.onDone = onDone
        triedFallback = false
        if (!available) { onDone("Voice input isn't available on this phone"); return }
        begin(preferOnDevice = true)
    }

    private fun begin(preferOnDevice: Boolean) {
        release()
        usingOnDevice = preferOnDevice && onDeviceAvailable()
        val r = runCatching {
            if (usingOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
        if (r == null) { finish("Voice input isn't available on this phone"); return }
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        listening = true
        level = 0f
        runCatching { r.startListening(intent) }.onFailure { finish("Couldn't start voice input") }
    }

    /** Stops and lets the recognizer deliver its final result. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        finish(null)
    }

    private fun finish(error: String?) {
        val was = listening
        listening = false
        level = 0f
        release()
        if (was || error != null) onDone(error)
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            // Typical range is about −2…10 dB.
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { level = 0f }
        override fun onPartialResults(partialResults: Bundle?) { best(partialResults)?.let(onText) }
        override fun onResults(results: Bundle?) {
            best(results)?.let(onText)
            finish(null)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onError(error: Int) {
            val retryable = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                error == SpeechRecognizer.ERROR_CLIENT
            if (usingOnDevice && !triedFallback && retryable && SpeechRecognizer.isRecognitionAvailable(context)) {
                triedFallback = true
                begin(preferOnDevice = false)
                return
            }
            finish(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null // heard nothing: just stop
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice needs a connection on this phone"
                    else -> "Voice input stopped"
                },
            )
        }
    }
}
