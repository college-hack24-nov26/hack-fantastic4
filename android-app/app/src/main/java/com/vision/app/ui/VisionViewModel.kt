package com.vision.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vision.app.domain.safety.NoOpSafetyPolicy
import com.vision.app.domain.safety.SafetyPolicy
import com.vision.app.domain.vision.MockVisionEngine
import com.vision.app.domain.vision.VisionEngine
import com.vision.app.domain.vision.VisionObservation
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
    val preferencesLoaded: Boolean = false,
    val observation: VisionObservation? = null,
    val isLoading: Boolean = false,
)

class VisionViewModel(application: Application) : AndroidViewModel(application) {
    private val engine: VisionEngine = MockVisionEngine()
    private val safetyPolicy: SafetyPolicy = NoOpSafetyPolicy()
    private val preferences = VisionPreferencesRepository.from(application)
    private val mutableState = MutableStateFlow(VisionUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences.collect { saved ->
                mutableState.update { it.copy(language = saved.language, speechRate = saved.speechRate, preferencesLoaded = true) }
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
