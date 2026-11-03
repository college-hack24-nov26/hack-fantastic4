package com.vision.app.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.vision.app.domain.voice.DialogueAction
import com.vision.app.domain.voice.DialogueEngine
import com.vision.app.settings.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AssistantPhase { Idle, Listening, Processing, Speaking, Error, Unavailable }
enum class ModelPhase { Missing, Loading, Ready, Error }

data class AssistantState(
    val phase: AssistantPhase = AssistantPhase.Idle,
    val heard: String = "",
    val reply: String = "",
    val recognitionError: Int? = null,
    val modelPhase: ModelPhase = ModelPhase.Missing,
)

/** One foreground session. Commands and visual refusals never go through the optional LLM. */
class VoiceAssistantSession(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val input = OfflineSpeechInput(appContext)
    private val output = AndroidVoiceOutput(appContext)
    private val dialogue = DialogueEngine()
    private val modelStore = LocalConversationModel(appContext)
    private val conversation = LiteRtConversationEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(AssistantState())
    val state = mutableState.asStateFlow()
    val onDeviceRecognitionAvailable get() = input.available
    private var generation = 0
    private var closed = false

    init { if (modelStore.installedFile.isFile) loadModel() }

    fun importModel(uri: Uri) {
        if (closed) return
        mutableState.value = mutableState.value.copy(modelPhase = ModelPhase.Loading)
        scope.launch {
            try {
                modelStore.import(uri)
                loadModel()
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(modelPhase = ModelPhase.Error)
            }
        }
    }

    private fun loadModel() {
        mutableState.value = mutableState.value.copy(modelPhase = ModelPhase.Loading)
        scope.launch {
            try {
                conversation.load(modelStore.installedFile)
                mutableState.value = mutableState.value.copy(modelPhase = ModelPhase.Ready)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(modelPhase = ModelPhase.Error)
            }
        }
    }

    fun startListening(language: UiLanguage, rate: Float, onLanguageChange: (UiLanguage) -> Unit, onRateChange: (Float) -> Unit) {
        if (closed) return
        if (!input.available) {
            mutableState.value = mutableState.value.copy(phase = AssistantPhase.Unavailable)
            return
        }
        val token = ++generation
        input.cancel()
        output.stop()
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Listening, recognitionError = null, heard = "", reply = "")
        handler.postDelayed({
            if (closed || token != generation) return@postDelayed
            val started = input.start(language,
                onListening = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Listening) },
                onResult = { result -> if (token == generation) process(result, language, rate, onLanguageChange, onRateChange, token, fromSpeech = true) },
                onError = { error -> if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error, recognitionError = error) },
            )
            if (!started) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Unavailable)
        }, 250)
    }

    /** Lets users exercise commands and the imported model without microphone recognition. */
    fun submitText(text: String, language: UiLanguage, rate: Float,
        onLanguageChange: (UiLanguage) -> Unit, onRateChange: (Float) -> Unit) {
        if (closed || text.isBlank()) return
        val token = ++generation
        input.cancel()
        output.stop()
        process(SpeechResult(text.trim(), listOf(text.trim()), null), language, rate,
            onLanguageChange, onRateChange, token, fromSpeech = false)
    }

    private fun process(result: SpeechResult, language: UiLanguage, rate: Float,
        onLanguageChange: (UiLanguage) -> Unit, onRateChange: (Float) -> Unit, token: Int, fromSpeech: Boolean) {
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Processing, heard = result.text)
        val command = dialogue.respond(result.text, language, rate)
        // This Samsung reports 0.0 even for plausible hypotheses. A score alone must not
        // suppress conversation. Ask again only for an unrecognized, very short utterance.
        if (fromSpeech && !command.handled && result.text.trim().split(Regex("\\s+")).size < 2) {
            speakReply(if (language == UiLanguage.Hinglish) "Mujhe clear nahi suna. Dobara bolenge?" else "I didn't catch that. Please try again.", language, rate, token)
            return
        }
        var responseLanguage = language
        var responseRate = rate
        when (val action = command.action) {
            DialogueAction.None -> Unit
            DialogueAction.StopSpeaking -> {
                output.stop()
                mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle, reply = command.text)
                return
            }
            is DialogueAction.SetLanguage -> { responseLanguage = action.language; onLanguageChange(action.language) }
            is DialogueAction.SetRate -> { responseRate = action.rate; onRateChange(action.rate) }
        }
        if (command.handled) {
            speakReply(command.text, responseLanguage, responseRate, token)
        } else if (conversation.isReady) {
            scope.launch {
                val generated = try { conversation.reply(result.text, language) } catch (_: Exception) { "" }
                if (closed || token != generation) return@launch
                speakReply(generated.ifBlank {
                    if (language == UiLanguage.Hinglish) "Abhi jawab nahi bana paaya. Dobara try karein." else "I couldn't answer that. Please try again."
                }, language, rate, token)
            }
        } else {
            speakReply(if (language == UiLanguage.Hinglish)
                "Offline conversation model abhi import nahi hua. Main time, date aur settings mein help kar sakta hoon."
            else "The offline conversation model is not imported yet. I can help with time, date, and settings.", language, rate, token)
        }
    }

    private fun speakReply(text: String, language: UiLanguage, rate: Float, token: Int) {
        if (closed || token != generation) return
        mutableState.value = mutableState.value.copy(reply = text)
        val queued = output.speak(text, language == UiLanguage.Hinglish, rate,
            onStarted = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Speaking) },
            onFinished = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle) },
            onError = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error) },
        )
        if (!queued) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error)
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
        speakReply(text, language, rate, ++generation)
    }

    override fun close() {
        closed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        input.close()
        output.close()
        conversation.close()
    }
}
