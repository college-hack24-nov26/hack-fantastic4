package com.vision.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class InstalledVoice(val name: String, val locale: String, val quality: Int)
data class VoiceInventory(val engine: String?, val voices: List<InstalledVoice>)
data class PreviewAttempt(val queued: Boolean, val selectedVoice: InstalledVoice? = null)

/** Speech output for setup preview; it does not record or recognize speech. */
class AndroidVoiceOutput(context: Context) : AutoCloseable {
    private val utteranceNumber = AtomicLong()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableInventory = MutableStateFlow(VoiceInventory(null, emptyList()))
    val inventory = mutableInventory.asStateFlow()
    private var tts: TextToSpeech? = null
    @Volatile private var closed = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (!closed && status == TextToSpeech.SUCCESS) {
                val output = tts
                mutableInventory.value = VoiceInventory(
                    output?.defaultEngine,
                    output?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
                        .filter { it.locale.language in setOf("en", "hi") }
                        .sortedWith(compareBy<Voice> { it.locale.toLanguageTag() }.thenByDescending { it.quality })
                        .map { it.info() },
                )
                mutableReady.value = true
            }
        }
    }

    fun preview(hinglish: Boolean, speechRate: Float): PreviewAttempt {
        val output = tts ?: return PreviewAttempt(false)
        if (!mutableReady.value) return PreviewAttempt(false)
        val voice = findVoice(output, hinglish) ?: return PreviewAttempt(false)
        if (output.setVoice(voice) != TextToSpeech.SUCCESS) return PreviewAttempt(false)
        output.setSpeechRate(speechRate.coerceIn(0.7f, 1.3f))
        val sample = when {
            !hinglish -> "Hello. Vision is ready for a voice check."
            voice.locale.language == "hi" -> "विज़न रेडी है। शुरू करने के लिए टैप करें।"
            else -> "Vision ready hai. Shuru karne ke liye tap karein."
        }
        val queued = output.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "vision-voice-preview") == TextToSpeech.SUCCESS
        return PreviewAttempt(queued, voice.info())
    }

    fun speak(
        text: String,
        hinglish: Boolean,
        speechRate: Float,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
        onError: () -> Unit,
    ): Boolean {
        val output = tts ?: return false
        if (!mutableReady.value) return false
        val voice = findVoice(output, hinglish) ?: return false
        if (output.setVoice(voice) != TextToSpeech.SUCCESS) return false
        if (output.setSpeechRate(speechRate.coerceIn(0.7f, 1.3f)) != TextToSpeech.SUCCESS) return false
        val id = "vision-assistant-${utteranceNumber.incrementAndGet()}"
        output.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { if (utteranceId == id) onStarted() }
            override fun onDone(utteranceId: String) { if (utteranceId == id) onFinished() }
            override fun onError(utteranceId: String) { if (utteranceId == id) onError() }
        })
        return output.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
    }

    fun stop() { tts?.stop() }

    private fun findVoice(output: TextToSpeech, hinglish: Boolean): Voice? {
        val candidates = output.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        val preferred = if (hinglish) listOf("hi-IN", "hi", "en-IN", "en") else listOf("en-IN", "en-US", "en")
        return preferred.firstNotNullOfOrNull { tag ->
            candidates.filter { candidate ->
                if (tag.contains('-')) candidate.locale.toLanguageTag().equals(tag, ignoreCase = true)
                else candidate.locale.language == tag
            }.maxByOrNull { it.quality }
        }
    }

    override fun close() {
        closed = true
        tts?.stop()
        tts?.shutdown()
        tts = null
        mutableReady.value = false
    }
}

private fun Voice.info() = InstalledVoice(name, locale.toLanguageTag(), quality)
