package com.vision.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vision.app.domain.safety.NoOpSafetyPolicy
import com.vision.app.domain.safety.SafetyPolicy
import com.vision.app.domain.vision.MockVisionEngine
import com.vision.app.domain.vision.VisionEngine
import com.vision.app.domain.vision.VisionObservation
import com.vision.app.detector.SceneObservations
import com.vision.app.domain.voice.CameraCommandPort
import com.vision.app.domain.voice.CameraRuntimeStatus
import com.vision.app.voice.VoiceAssistantSession
import com.vision.app.settings.UiLanguage
import com.vision.app.settings.VisionPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisionUiState(
    val onboardingPage: Int = 0,
    val language: UiLanguage = UiLanguage.English,
    val speechRate: Float = 1f,
    val automaticAnnouncements: Boolean = false,
    val cameraRequested: Boolean = false,
    val cameraStatus: CameraRuntimeStatus = CameraRuntimeStatus.Off,
    val cameraNavigationSeq: Int = 0,
    val preferencesLoaded: Boolean = false,
    val observation: VisionObservation? = null,
    val isLoading: Boolean = false,
)

class VisionViewModel(application: Application) : AndroidViewModel(application), CameraCommandPort {
    val scenes = SceneObservations()
    private var sharedVoiceSession: VoiceAssistantSession? = null
    private var pendingCameraStart: ((Boolean) -> Unit)? = null
    private var pendingCameraStop: ((Boolean) -> Unit)? = null
    fun voiceSession(): VoiceAssistantSession = sharedVoiceSession ?: VoiceAssistantSession(getApplication(), scenes, this).also {
        sharedVoiceSession = it
    }
    override val cameraStatus: CameraRuntimeStatus get() = mutableState.value.cameraStatus
    private val engine: VisionEngine = MockVisionEngine()
    private val safetyPolicy: SafetyPolicy = NoOpSafetyPolicy()
    private val preferences = VisionPreferencesRepository.from(application)
    private val mutableState = MutableStateFlow(VisionUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences.collect { saved ->
                mutableState.update { it.copy(language = saved.language, speechRate = saved.speechRate,
                    automaticAnnouncements = saved.automaticAnnouncements, preferencesLoaded = true) }
            }
        }
    }

    fun nextOnboardingPage(): Boolean {
        val current = mutableState.value.onboardingPage
        if (current >= 2) return false
        mutableState.update { it.copy(onboardingPage = current + 1) }
        return true
    }

    fun previousOnboardingPage(): Boolean {
        val current = mutableState.value.onboardingPage
        if (current <= 0) return false
        mutableState.update { it.copy(onboardingPage = current - 1) }
        return true
    }

    fun setLanguage(language: UiLanguage) {
        mutableState.update { it.copy(language = language) }
        viewModelScope.launch { preferences.setLanguage(language) }
    }

    fun setSpeechRate(rate: Float) {
        val bounded = rate.coerceIn(0.7f, 1.3f)
        mutableState.update { it.copy(speechRate = bounded) }
        viewModelScope.launch { preferences.setSpeechRate(bounded) }
    }

    fun setAutomaticAnnouncements(enabled: Boolean) {
        mutableState.update { it.copy(automaticAnnouncements = enabled) }
        viewModelScope.launch { preferences.setAutomaticAnnouncements(enabled) }
    }

    override fun setAnnouncementsByVoice(enabled: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                preferences.setAutomaticAnnouncements(enabled)
                mutableState.update { it.copy(automaticAnnouncements = enabled) }
                onComplete(true)
            } catch (_: Exception) { onComplete(false) }
        }
    }

    fun openCamera() {
        mutableState.update { it.copy(cameraRequested = true, cameraStatus = CameraRuntimeStatus.Starting) }
    }

    override fun startCamera(onComplete: (Boolean) -> Unit) {
        if (cameraStatus == CameraRuntimeStatus.Active) { onComplete(true); return }
        pendingCameraStart?.invoke(false)
        pendingCameraStart = onComplete
        mutableState.update { it.copy(cameraRequested = true, cameraStatus = CameraRuntimeStatus.Starting,
            cameraNavigationSeq = it.cameraNavigationSeq + 1) }
    }

    override fun stopCamera(onComplete: (Boolean) -> Unit) {
        pendingCameraStart?.invoke(false)
        pendingCameraStart = null
        if (cameraStatus == CameraRuntimeStatus.Off || cameraStatus == CameraRuntimeStatus.Failed) {
            mutableState.update { it.copy(cameraRequested = false, cameraStatus = CameraRuntimeStatus.Off) }
            scenes.snapshot.value.sessionId?.let(scenes::stop)
            onComplete(true)
            return
        }
        pendingCameraStop?.invoke(false)
        pendingCameraStop = onComplete
        mutableState.update { it.copy(cameraRequested = false) }
        // CameraMonitor.onDispose acknowledges after analyzer and scene invalidation.
    }

    fun cameraStarted() {
        if (!mutableState.value.cameraRequested) return
        mutableState.update { it.copy(cameraStatus = CameraRuntimeStatus.Active) }
        pendingCameraStart?.invoke(true)
        pendingCameraStart = null
    }

    fun cameraStopped() {
        scenes.snapshot.value.sessionId?.let(scenes::stop)
        mutableState.update { it.copy(cameraRequested = false,
            cameraStatus = if (it.cameraStatus == CameraRuntimeStatus.Failed) CameraRuntimeStatus.Failed else CameraRuntimeStatus.Off) }
        pendingCameraStart?.invoke(false)
        pendingCameraStart = null
        pendingCameraStop?.invoke(true)
        pendingCameraStop = null
    }

    fun cameraFailed() {
        scenes.snapshot.value.sessionId?.let(scenes::stop)
        mutableState.update { it.copy(cameraRequested = false, cameraStatus = CameraRuntimeStatus.Failed) }
        pendingCameraStart?.invoke(false)
        pendingCameraStart = null
        pendingCameraStop?.invoke(false)
        pendingCameraStop = null
    }

    override fun onCleared() {
        sharedVoiceSession?.close()
        sharedVoiceSession = null
        super.onCleared()
    }

    fun loadMockObservation() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            val observation = engine.observe()
            // The current policy never turns mock output into a safety warning.
            safetyPolicy.warningFor(observation)
            mutableState.update { it.copy(observation = observation, isLoading = false) }
        }
    }
}
