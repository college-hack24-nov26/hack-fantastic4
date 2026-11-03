package com.vision.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.vision.app.settings.UiLanguage

data class SpeechResult(val text: String, val alternatives: List<String>, val confidence: Float?)

/** Uses Android's on-device recognizer only. No generic or network recognizer fallback. */
class OfflineSpeechInput(private val context: Context) : AutoCloseable {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    val available: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(language: UiLanguage, onListening: () -> Unit, onResult: (SpeechResult) -> Unit, onError: (Int) -> Unit): Boolean {
        val onDeviceAvailable = available
        val locale = if (language == UiLanguage.Hinglish) "hi-IN" else "en-IN"
        Log.i(TAG, "start requestedLocale=$locale onDeviceAvailable=$onDeviceAvailable " +
            "defaultConfiguredService=${Settings.Secure.getString(context.contentResolver, "voice_recognition_service")}")
        if (!onDeviceAvailable) return false
        cancel()
        val token = ++generation
        val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = speech
        var ready = false
        var speechStarted = false
        var speechEnded = false
        var lastRmsLog = 0L
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (token == generation) { ready = true; Log.i(TAG, "onReadyForSpeech locale=$locale"); onListening() }
            }
            override fun onBeginningOfSpeech() {
                if (token == generation) { speechStarted = true; Log.i(TAG, "onBeginningOfSpeech locale=$locale") }
            }
            override fun onRmsChanged(rmsdB: Float) {
                if (token == generation && SystemClock.elapsedRealtime() - lastRmsLog >= 750L) {
                    lastRmsLog = SystemClock.elapsedRealtime()
                    Log.d(TAG, "onRmsChanged level=$rmsdB")
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (token == generation) { speechEnded = true; Log.i(TAG, "onEndOfSpeech locale=$locale") }
            }
            override fun onError(error: Int) {
                if (token == generation) {
                    Log.w(TAG, "onError code=$error description=${describeError(error)} ready=$ready began=$speechStarted ended=$speechEnded locale=$locale")
                    recognizer = null
                    speech.destroy()
                    onError(error)
                }
            }
            override fun onResults(results: Bundle?) {
                if (token != generation) return
                val alternatives = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                // The service orders hypotheses; its confidence values are diagnostic only.
                val ranked = 0
                val phrase = alternatives.getOrNull(ranked).orEmpty()
                val confidence = scores?.getOrNull(ranked)?.takeIf { it >= 0f }
                Log.i(TAG, "onResults alternatives=${alternatives.size} confidence=${confidence ?: "unavailable"} " +
                    "ready=$ready began=$speechStarted ended=$speechEnded locale=$locale")
                recognizer = null
                speech.destroy()
                if (phrase.isBlank()) onError(SpeechRecognizer.ERROR_NO_MATCH) else onResult(SpeechResult(phrase, alternatives, confidence))
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            if (Build.VERSION.SDK_INT >= 33) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, arrayListOf(
                    "What time is it", "What is the date", "Repeat that", "Stop speaking", "Speak slower", "Speak faster", "Switch to Hinglish", "Switch to English",
                    "Kitne baje hain", "Aaj ki date kya hai", "Phir se bolo", "Bolna band karo", "Dheere bolo", "Tez bolo",
                ))
            }
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
        }
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                speech.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        Log.i(TAG, "onDeviceSupport locale=$locale installed=${support.installedOnDeviceLanguages} " +
                            "supportedNotInstalled=${support.supportedOnDeviceLanguages} pending=${support.pendingOnDeviceLanguages}")
                    }
                    override fun onError(error: Int) {
                        Log.w(TAG, "onDeviceSupportCheckError code=$error description=${describeError(error)}; " +
                            "continuing with the on-device recognizer only")
                    }
                })
            }
            speech.startListening(intent)
            true
        } catch (error: Exception) {
            Log.e(TAG, "startListening failed for on-device locale=$locale", error)
            recognizer = null
            speech.destroy()
            false
        }
    }

    fun cancel() {
        generation++
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun close() = cancel()

    companion object {
        private const val TAG = "VisionSpeech"

        fun describeError(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
            SpeechRecognizer.ERROR_NETWORK -> "network error"
            SpeechRecognizer.ERROR_AUDIO -> "audio capture error"
            SpeechRecognizer.ERROR_SERVER -> "recognition service error"
            SpeechRecognizer.ERROR_CLIENT -> "client error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech input"
            SpeechRecognizer.ERROR_NO_MATCH -> "no recognition match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission denied"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "recognition service disconnected"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "requested language unsupported"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "offline language model unavailable"
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "support query unavailable"
            SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "model download events unavailable"
            else -> "unknown recognition error"
        }
    }
}
