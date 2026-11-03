package com.vision.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.vision.app.domain.voice.DialogueAction
import com.vision.app.domain.voice.DialogueEngine
import com.vision.app.settings.UiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantPhase { Idle, Listening, Processing, Speaking, Error, Unavailable }

data class AssistantState(
    val phase: AssistantPhase = AssistantPhase.Idle,
    val heard: String = "",
    val reply: String = "",
    val recognitionError: Int? = null,
)

/** One foreground session. Recognition is cancelled before TTS, and TTS is stopped before listening. */
class VoiceAssistantSession(context: Context) : AutoCloseable {
    private val input = OfflineSpeechInput(context.applicationContext)
    private val output = AndroidVoiceOutput(context.applicationContext)
    private val dialogue = DialogueEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(AssistantState())
    val state = mutableState.asStateFlow()
    val onDeviceRecognitionAvailable get() = input.available
    private var generation = 0
    private var closed = false

    fun startListening(language: UiLanguage, rate: Float,
        onLanguageChange: (UiLanguage) -> Unit, onRateChange: (Float) -> Unit) {
        if (closed) return
        if (!input.available) {
            mutableState.value = mutableState.value.copy(phase = AssistantPhase.Unavailable)
            return
        }
        val token = ++generation
        input.cancel()
        output.stop()
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Listening, recognitionError = null)
        handler.postDelayed({
            if (closed || token != generation) return@postDelayed
            val started = input.start(language,
                onListening = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Listening) },
                onResult = { transcript ->
                    if (token == generation) {
                        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Processing, heard = transcript)
                        val answer = dialogue.respond(transcript, language, rate)
                        var responseLanguage = language
                        var responseRate = rate
                        when (val action = answer.action) {
                            DialogueAction.None -> Unit
                            DialogueAction.StopSpeaking -> {
                                output.stop()
                                mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle, reply = answer.text)
                                return@start
                            }
                            is DialogueAction.SetLanguage -> { responseLanguage = action.language; onLanguageChange(action.language) }
                            is DialogueAction.SetRate -> { responseRate = action.rate; onRateChange(action.rate) }
                        }
                        mutableState.value = mutableState.value.copy(reply = answer.text)
                        val queued = output.speak(answer.text, responseLanguage == UiLanguage.Hinglish, responseRate,
                            onStarted = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Speaking) },
                            onFinished = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle) },
                            onError = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error) },
                        )
                        if (!queued) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error)
                    }
                },
                onError = { error -> if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error, recognitionError = error) },
            )
            if (!started) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Unavailable)
        }, 250)
    }

    fun stopListening() {
        generation++
        input.cancel()
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle)
    }

    fun interruptSpeech() {
        generation++
        output.stop()
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle)
    }

    /** Reserved for a future validated safety event source; never called by the mock engine. */
    fun announcePriority(text: String, language: UiLanguage, rate: Float) {
        stopListening()
        output.stop()
        val token = ++generation
        output.speak(text, language == UiLanguage.Hinglish, rate,
            onStarted = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Speaking) },
            onFinished = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle) },
            onError = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error) },
        )
    }

    override fun close() {
        closed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        input.close()
        output.close()
    }
}
