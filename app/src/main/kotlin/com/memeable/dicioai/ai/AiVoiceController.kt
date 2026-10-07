package com.memeable.dicioai.ai

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

enum class AiVoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

class AiVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    val state: MutableState<AiVoiceState> = mutableStateOf(AiVoiceState.IDLE)
    val partialText: MutableState<String> = mutableStateOf("")
    val handsFreeEnabled: MutableState<Boolean> = mutableStateOf(
        prefs.getBoolean(KEY_HANDS_FREE, false)
    )
    val speakRepliesEnabled: MutableState<Boolean> = mutableStateOf(
        prefs.getBoolean(KEY_SPEAK_REPLIES, true)
    )

    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onSpeechFinished: (() -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var manualStop = false
    private var destroyed = false
    private var utteranceCounter = 0

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            state.value = AiVoiceState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            state.value = AiVoiceState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (!manualStop) state.value = AiVoiceState.PROCESSING
        }

        override fun onError(error: Int) {
            if (manualStop) {
                manualStop = false
                state.value = AiVoiceState.IDLE
                partialText.value = ""
                return
            }

            partialText.value = ""
            state.value = AiVoiceState.ERROR

            val retryable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

            if (!retryable) onError?.invoke(errorDescription(error))

            if (handsFreeEnabled.value && retryable) {
                handler.postDelayed({
                    if (!destroyed && handsFreeEnabled.value && state.value != AiVoiceState.LISTENING) {
                        startListening()
                    }
                }, 650L)
            }
        }

        override fun onResults(results: Bundle?) {
            if (manualStop) {
                manualStop = false
                state.value = AiVoiceState.IDLE
                partialText.value = ""
                return
            }

            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            partialText.value = ""
            if (spoken.isBlank()) {
                state.value = AiVoiceState.ERROR
                if (handsFreeEnabled.value) handler.postDelayed({ startListening() }, 500L)
                return
            }

            state.value = AiVoiceState.PROCESSING
            onResult?.invoke(spoken)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialText.value = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                textToSpeech?.language = Locale.getDefault()
                pendingSpeech?.let {
                    pendingSpeech = null
                    speak(it)
                }
            } else {
                ttsReady = false
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handler.post {
                    if (!destroyed) state.value = AiVoiceState.SPEAKING
                }
            }

            override fun onDone(utteranceId: String?) {
                handler.post {
                    if (!destroyed) {
                        state.value = AiVoiceState.IDLE
                        onSpeechFinished?.invoke()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                handler.post {
                    if (!destroyed) {
                        state.value = AiVoiceState.IDLE
                        onSpeechFinished?.invoke()
                    }
                }
            }
        })
    }

    fun startListening() {
        if (destroyed) return
        if (state.value == AiVoiceState.LISTENING) return

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            state.value = AiVoiceState.ERROR
            onError?.invoke("No speech recognition service is available.")
            return
        }

        if (recognizer == null) {
            recognizer = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                }
            }.getOrElse {
                state.value = AiVoiceState.ERROR
                onError?.invoke("Speech recognition is unavailable on this device.")
                return
            }
            recognizer?.setRecognitionListener(recognitionListener)
        }

        textToSpeech?.stop()
        handler.removeCallbacksAndMessages(null)
        manualStop = false
        partialText.value = ""
        state.value = AiVoiceState.LISTENING

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        runCatching {
            recognizer?.startListening(intent)
        }.onFailure {
            state.value = AiVoiceState.ERROR
            onError?.invoke("Could not start voice input.")
        }
    }

    fun stop() {
        if (destroyed) return
        manualStop = true
        recognizer?.cancel()
        textToSpeech?.stop()
        handler.removeCallbacksAndMessages(null)
        partialText.value = ""
        state.value = AiVoiceState.IDLE
    }

    fun speak(text: String) {
        if (destroyed || !speakRepliesEnabled.value) return
        val clean = cleanForSpeech(text)
        if (clean.isBlank()) return

        if (!ttsReady) {
            pendingSpeech = clean
            return
        }

        state.value = AiVoiceState.SPEAKING
        val utteranceId = "dicio-agent-${++utteranceCounter}"
        val result = runCatching {
            textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }.getOrElse { TextToSpeech.ERROR }

        if (result != TextToSpeech.SUCCESS) {
            state.value = AiVoiceState.IDLE
            onSpeechFinished?.invoke()
        }
    }

    fun toggleHandsFree(): Boolean {
        val enabled = !handsFreeEnabled.value
        handsFreeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HANDS_FREE, enabled).apply()

        if (!enabled) {
            handler.removeCallbacksAndMessages(null)
            recognizer?.cancel()
            partialText.value = ""
            state.value = AiVoiceState.IDLE
        }
        return enabled
    }

    fun setHandsFreeEnabled(enabled: Boolean) {
        handsFreeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HANDS_FREE, enabled).apply()
    }

    fun toggleSpeakReplies(): Boolean {
        val enabled = !speakRepliesEnabled.value
        speakRepliesEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SPEAK_REPLIES, enabled).apply()

        if (!enabled) {
            textToSpeech?.stop()
            if (state.value == AiVoiceState.SPEAKING) state.value = AiVoiceState.IDLE
        }
        return enabled
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        textToSpeech?.shutdown()
        textToSpeech = null
        onResult = null
        onError = null
        onSpeechFinished = null
    }

    private fun cleanForSpeech(raw: String): String {
        val fence = 96.toChar().toString().repeat(3)
        return raw
            .replace(Regex("(?s)" + Regex.escape(fence) + ".*?" + Regex.escape(fence)), " code omitted ")
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            .replace(Regex("https?://\\S+"), " link ")
            .replace(Regex("[#*_>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(2000)
    }

    private fun errorDescription(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be accessed."
        SpeechRecognizer.ERROR_CLIENT -> "Voice input was interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "The speech service reported a network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
        SpeechRecognizer.ERROR_SERVER -> "The speech service reported an error."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "The speech service is receiving too many requests."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice input is already busy."
        else -> "Voice input failed."
    }

    companion object {
        private const val PREFS_NAME = "dicio_ai_voice"
        private const val KEY_HANDS_FREE = "hands_free"
        private const val KEY_SPEAK_REPLIES = "speak_replies"
    }
}
