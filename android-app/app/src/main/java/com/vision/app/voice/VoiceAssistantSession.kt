package com.vision.app.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.vision.app.detector.SceneObservations
import com.vision.app.domain.voice.AutoAnnouncementPolicy
import com.vision.app.domain.voice.CameraCommandPort
import com.vision.app.domain.voice.CameraRuntimeStatus
import com.vision.app.domain.voice.CameraVoiceCommand
import com.vision.app.domain.voice.CameraVoiceCommands
import com.vision.app.domain.voice.DialogueAction
import com.vision.app.domain.voice.DialogueEngine
import com.vision.app.domain.voice.VisualQuestionRouter
import com.vision.app.domain.voice.SpeechPriority
import com.vision.app.domain.voice.SpeechPriorityCoordinator
import com.vision.app.settings.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AssistantPhase { Idle, Listening, Recognizing, Processing, ExecutingCommand, GeneratingResponse, Speaking, Error, Unavailable }
enum class ModelPhase { Missing, Loading, Ready, Error }

data class AssistantState(
    val phase: AssistantPhase = AssistantPhase.Idle,
    val heard: String = "",
    val reply: String = "",
    val recognitionError: Int? = null,
    val modelPhase: ModelPhase = ModelPhase.Missing,
    val recentObservation: String = "",
)

/** One foreground session. Commands and visual refusals never go through the optional LLM. */
class VoiceAssistantSession(context: Context, private val scenes: SceneObservations,
    private val cameraCommands: CameraCommandPort) : AutoCloseable {
    private val appContext = context.applicationContext
    private val input = OfflineSpeechInput(appContext)
    private val output = AndroidVoiceOutput(appContext)
    private val dialogue = DialogueEngine()
    private val visual = VisualQuestionRouter()
    private val autoPolicy = AutoAnnouncementPolicy()
    private val speechCoordinator = SpeechPriorityCoordinator()
    private val modelStore = LocalConversationModel(appContext)
    private val conversation = LiteRtConversationEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(AssistantState())
    val state = mutableState.asStateFlow()
    val onDeviceRecognitionAvailable get() = input.available
    val cameraStatus get() = cameraCommands.cameraStatus
    private var generation = 0
    private var closed = false
    private var automaticAnnouncements = false
    private var automaticSpeaking = false
    private var userComposing = false
    private var currentLanguage = UiLanguage.English
    private var currentRate = 1f
    private var lastSpokenResponse: String? = null

    init {
        if (modelStore.installedFile.isFile) loadModel()
        scope.launch {
            scenes.snapshot.collect { snapshot ->
                if (!snapshot.cameraActive) {
                    autoPolicy.reset()
                    if (automaticSpeaking) interruptSpeech()
                } else if (automaticAnnouncements && !userComposing && mutableState.value.phase == AssistantPhase.Idle) {
                    val now = SystemClock.elapsedRealtime()
                    val fresh = scenes.read(now)
                    val key = autoPolicy.candidate(fresh, now)
                    if (key != null) {
                        val phrase = visual.respond("What do you see?", currentLanguage, fresh, now)
                        if (phrase != null && speakAutomatic(phrase, currentLanguage, currentRate)) {
                            autoPolicy.markAnnounced(key, now)
                        }
                    }
                }
            }
        }
    }

    fun updateSpeechSettings(language: UiLanguage, rate: Float, announcements: Boolean) {
        currentLanguage = language
        currentRate = rate
        if (automaticAnnouncements && !announcements && automaticSpeaking) interruptSpeech()
        automaticAnnouncements = announcements
        if (!announcements) autoPolicy.reset()
    }

    fun updateUserComposing(composing: Boolean) {
        userComposing = composing
        if (composing && automaticSpeaking) interruptSpeech()
    }

    private fun speakAutomatic(text: String, language: UiLanguage, rate: Float): Boolean {
        if (closed || mutableState.value.phase != AssistantPhase.Idle) return false
        val speechId = speechCoordinator.begin(SpeechPriority.AutomaticObservation) ?: return false
        val token = ++generation
        automaticSpeaking = true
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Speaking, recentObservation = text)
        val queued = output.speak(text, language == UiLanguage.Hinglish, rate,
            onStarted = { Log.i(TAG, "automatic TTS started") },
            onFinished = { handler.post { if (token == generation && speechCoordinator.isCurrent(speechId)) {
                Log.i(TAG, "automatic TTS completed")
                speechCoordinator.complete(speechId); automaticSpeaking = false
                mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle) } } },
            onError = { handler.post { if (token == generation && speechCoordinator.isCurrent(speechId)) {
                Log.w(TAG, "automatic TTS failed")
                speechCoordinator.complete(speechId); automaticSpeaking = false
                mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error) } } },
        )
        Log.i(TAG, "automatic TTS queued=$queued")
        if (!queued) { speechCoordinator.complete(speechId); automaticSpeaking = false; mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle) }
        return queued
    }

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
        automaticSpeaking = false
        speechCoordinator.cancel()
        input.cancel()
        output.stop()
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Listening, recognitionError = null, heard = "", reply = "")
        handler.postDelayed({
            if (closed || token != generation) return@postDelayed
            val started = input.start(language,
                onListening = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Listening) },
                onRecognizing = { if (token == generation) mutableState.value = mutableState.value.copy(phase = AssistantPhase.Recognizing) },
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
        automaticSpeaking = false
        speechCoordinator.cancel()
        input.cancel()
        output.stop()
        process(SpeechResult(text.trim(), listOf(text.trim()), null), language, rate,
            onLanguageChange, onRateChange, token, fromSpeech = false)
    }

    private fun process(result: SpeechResult, language: UiLanguage, rate: Float,
        onLanguageChange: (UiLanguage) -> Unit, onRateChange: (Float) -> Unit, token: Int, fromSpeech: Boolean) {
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Processing, heard = result.text)
        val cameraCommand = CameraVoiceCommands.parse(result.text)
        if (cameraCommand != null) {
            executeCameraCommand(cameraCommand, language, rate, token)
            return
        }
        if (listOf("repeat", "say that again", "phir se", "dobara", "दोबारा").any {
                result.text.contains(it, ignoreCase = true) } && lastSpokenResponse != null) {
            speakReply(lastSpokenResponse!!, language, rate, token)
            return
        }
        val visualReply = visual.respond(result.text, language, scenes.read(SystemClock.elapsedRealtime()), SystemClock.elapsedRealtime())
        if (visualReply != null) {
            Log.i(TAG, "visual question handled by deterministic scene router")
            mutableState.value = mutableState.value.copy(recentObservation = visualReply)
            speakReply(visualReply, language, rate, token)
            return
        }
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
            mutableState.value = mutableState.value.copy(phase = AssistantPhase.GeneratingResponse)
            scope.launch {
                val generated = try { conversation.reply(result.text, language) } catch (_: Exception) { "" }
                if (closed || token != generation) return@launch
                speakReply(generated.ifBlank {
                    if (language == UiLanguage.Hinglish) "Abhi jawab nahi bana paaya. Dobara try karein." else "I couldn't answer that. Please try again."
                }, language, rate, token, SpeechPriority.AssistantResponse)
            }
        } else {
            speakReply(if (language == UiLanguage.Hinglish)
                "Offline conversation model abhi import nahi hua. Main time, date aur settings mein help kar sakta hoon."
            else "The offline conversation model is not imported yet. I can help with time, date, and settings.", language, rate, token)
        }
    }

    private fun executeCameraCommand(command: CameraVoiceCommand, language: UiLanguage, rate: Float, token: Int) {
        val hinglish = language == UiLanguage.Hinglish
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.ExecutingCommand)
        fun finish(success: Boolean, english: String, local: String, failure: String) {
            if (closed || token != generation) return
            speakReply(if (success) { if (hinglish) local else english } else failure, language, rate, token)
        }
        when (command) {
            CameraVoiceCommand.Start -> {
                cameraCommands.startCamera { success -> finish(success,
                    "Camera is active. Detection is experimental.", "Camera chalu hai. Detection experimental hai.",
                    if (hinglish) "Camera shuru nahi ho saka. Permission aur device check karein." else "Camera could not start. Check permission and device status.") }
                handler.postDelayed({ if (!closed && token == generation && mutableState.value.phase == AssistantPhase.ExecutingCommand)
                    finish(false, "", "", if (hinglish) "Camera start hone mein der ho rahi hai." else "Camera start timed out.") }, 15_000)
            }
            CameraVoiceCommand.Stop -> cameraCommands.stopCamera { success -> finish(success,
                "Camera stopped.", "Camera band hai.",
                if (hinglish) "Camera band nahi ho saka." else "Camera could not stop.") }
            CameraVoiceCommand.EnableAnnouncements, CameraVoiceCommand.DisableAnnouncements -> {
                val enabled = command == CameraVoiceCommand.EnableAnnouncements
                cameraCommands.setAnnouncementsByVoice(enabled) { success -> finish(success,
                    if (enabled) "Automatic object announcements are on." else "Automatic object announcements are off.",
                    if (enabled) "Automatic object announcements chalu hain." else "Automatic object announcements band hain.",
                    if (hinglish) "Announcement setting badal nahi saki." else "Announcement setting could not be changed.") }
            }
            CameraVoiceCommand.Status -> {
                val status = when (cameraCommands.cameraStatus) {
                    CameraRuntimeStatus.Off -> if (hinglish) "Camera band hai." else "Camera is off."
                    CameraRuntimeStatus.Starting -> if (hinglish) "Camera shuru ho raha hai." else "Camera is starting."
                    CameraRuntimeStatus.Active -> if (hinglish) "Camera chalu hai." else "Camera is active."
                    CameraRuntimeStatus.Failed -> if (hinglish) "Camera fail hua hai." else "Camera failed."
                }
                speakReply(status + if (hinglish) " Voice ek request ke liye sunta hai." else " Voice listens for one request at a time.", language, rate, token)
            }
        }
    }

    private fun speakReply(text: String, language: UiLanguage, rate: Float, token: Int,
        priority: SpeechPriority = SpeechPriority.UserResponse) {
        if (closed || token != generation) return
        val speechId = speechCoordinator.begin(priority) ?: return
        lastSpokenResponse = text
        mutableState.value = mutableState.value.copy(reply = text)
        val queued = output.speak(text, language == UiLanguage.Hinglish, rate,
            onStarted = { handler.post { if (token == generation && speechCoordinator.isCurrent(speechId)) {
                Log.i(TAG, "requested TTS started")
                mutableState.value = mutableState.value.copy(phase = AssistantPhase.Speaking) } } },
            onFinished = { handler.post { if (token == generation && speechCoordinator.isCurrent(speechId)) {
                Log.i(TAG, "requested TTS completed")
                speechCoordinator.complete(speechId); mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle) } } },
            onError = { handler.post { if (token == generation && speechCoordinator.isCurrent(speechId)) {
                Log.w(TAG, "requested TTS failed")
                speechCoordinator.complete(speechId); mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error) } } },
        )
        Log.i(TAG, "requested TTS queued=$queued priority=$priority")
        if (!queued) { speechCoordinator.complete(speechId); mutableState.value = mutableState.value.copy(phase = AssistantPhase.Error) }
    }

    fun stopListening() {
        if (mutableState.value.phase != AssistantPhase.Listening &&
            mutableState.value.phase != AssistantPhase.Recognizing) return
        generation++
        input.cancel()
        mutableState.value = mutableState.value.copy(phase = AssistantPhase.Idle)
    }

    fun interruptSpeech() {
        generation++
        automaticSpeaking = false
        speechCoordinator.cancel()
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
        speechCoordinator.cancel()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        input.close()
        output.close()
        conversation.close()
    }

    companion object { private const val TAG = "VisionVoice" }
}
