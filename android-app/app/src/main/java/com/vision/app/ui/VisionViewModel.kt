package com.vision.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vision.app.domain.safety.NoOpSafetyPolicy
import com.vision.app.domain.safety.SafetyPolicy
import com.vision.app.domain.vision.MockVisionEngine
import com.vision.app.domain.vision.VisionEngine
import com.vision.app.domain.vision.VisionObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisionUiState(
    val onboardingPage: Int = 0,
    val languageTag: String = "en-IN",
    val speechRate: Float = 1f,
    val observation: VisionObservation? = null,
    val isLoading: Boolean = false,
)

class VisionViewModel(
    private val engine: VisionEngine = MockVisionEngine(),
    private val safetyPolicy: SafetyPolicy = NoOpSafetyPolicy(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(VisionUiState())
    val state = mutableState.asStateFlow()

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

    fun setLanguage(tag: String) {
        mutableState.update { it.copy(languageTag = tag) }
    }

    fun setSpeechRate(rate: Float) {
        mutableState.update { it.copy(speechRate = rate.coerceIn(0.7f, 1.3f)) }
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
