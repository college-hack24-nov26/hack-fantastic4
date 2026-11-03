package com.vision.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Speech output for setup preview; it does not record or recognize speech. */
class AndroidVoiceOutput(context: Context) : AutoCloseable {
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private var tts: TextToSpeech? = null
    @Volatile private var closed = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (!closed) mutableReady.value = status == TextToSpeech.SUCCESS
        }
    }

    fun preview(languageTag: String, speechRate: Float): Boolean {
        val output = tts ?: return false
        if (!mutableReady.value) return false
        val locale = Locale.forLanguageTag(languageTag)
        val localVoice = output.voices?.firstOrNull { voice ->
            !voice.isNetworkConnectionRequired && voice.locale.language == locale.language && voice.locale.country == locale.country
        } ?: return false
        if (output.setVoice(localVoice) != TextToSpeech.SUCCESS) return false
        output.setSpeechRate(speechRate)
        return output.speak("Hello. This is a Vision voice preview.", TextToSpeech.QUEUE_FLUSH, null, "vision-voice-preview") == TextToSpeech.SUCCESS
    }

    override fun close() {
        closed = true
        tts?.stop()
        tts?.shutdown()
        tts = null
        mutableReady.value = false
    }
}
