package com.vision.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vision.app.domain.vision.VisionObservation
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.theme.VisionColors
import com.vision.app.ui.VisionStrings
import com.vision.app.settings.UiLanguage
import com.vision.app.detector.SceneObservations
import com.vision.app.voice.VoiceAssistantSession
import com.vision.app.domain.voice.CameraRuntimeStatus

@Composable
fun ActiveAssistantScreen(
    observation: VisionObservation?,
    strings: VisionStrings,
    language: UiLanguage,
    speechRate: Float,
    onLanguageChange: (UiLanguage) -> Unit,
    onRateChange: (Float) -> Unit,
    scenes: SceneObservations,
    automaticAnnouncements: Boolean,
    onAutomaticAnnouncementsChange: (Boolean) -> Unit,
    voiceSession: VoiceAssistantSession,
    cameraStatus: CameraRuntimeStatus,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame {
        VisionTextButton(strings.assistantBack, onBack)
        Text(strings.assistantTitle, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        VisionCard {
            Text(strings.simulated, style = MaterialTheme.typography.titleLarge, color = VisionColors.Cream)
            Text(
                strings.assistantWarning,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        VoiceAssistantPanel(strings, language, speechRate, onLanguageChange, onRateChange,
            scenes, automaticAnnouncements, onAutomaticAnnouncementsChange, voiceSession, cameraStatus)
        Text(strings.sampleTitle, style = MaterialTheme.typography.titleLarge)
        Text(
            if (isLoading) strings.sampleLoading else if (observation == null) strings.sampleEmpty else if (observation.isSimulated) strings.sampleDescription else observation.description,
            style = MaterialTheme.typography.bodyLarge,
            color = VisionColors.MutedCream,
        )
        VisionButton(strings.refreshSample, onRefresh, enabled = !isLoading)
        Text(
            strings.assistantCaution,
            style = MaterialTheme.typography.bodyMedium,
            color = VisionColors.MutedCream,
        )
    }
}
