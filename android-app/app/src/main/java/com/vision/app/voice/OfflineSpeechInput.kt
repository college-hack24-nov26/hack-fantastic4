package com.vision.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vision.app.settings.UiLanguage

/** Uses Android's on-device recognizer only. No generic or network recognizer fallback. */
class OfflineSpeechInput(private val context: Context) : AutoCloseable {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    val available: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(language: UiLanguage, onListening: () -> Unit, onResult: (String) -> Unit, onError: (Int) -> Unit): Boolean {
        if (!available) return false
        cancel()
        val token = ++generation
        val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if (token == generation) onListening() }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) { if (token == generation) { recognizer = null; speech.destroy(); onError(error) } }
            override fun onResults(results: Bundle?) {
                if (token != generation) return
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                recognizer = null
                speech.destroy()
                if (phrase.isBlank()) onError(SpeechRecognizer.ERROR_NO_MATCH) else onResult(phrase)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val locale = if (language == UiLanguage.Hinglish) "hi-IN" else "en-IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        return try {
            speech.startListening(intent)
            true
        } catch (error: Exception) {
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
}
