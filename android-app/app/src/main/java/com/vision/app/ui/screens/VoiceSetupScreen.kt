package com.vision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.components.WaveMark
import com.vision.app.ui.theme.VisionColors
import com.vision.app.voice.AndroidVoiceOutput
import com.vision.app.settings.UiLanguage
import com.vision.app.ui.VisionStrings

@Composable
fun VoiceSetupScreen(
    language: UiLanguage,
    strings: VisionStrings,
    speechRate: Float,
    onLanguageChange: (UiLanguage) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val output = remember(context) { AndroidVoiceOutput(context) }
    val isReady by output.ready.collectAsStateWithLifecycle()
    val inventory by output.inventory.collectAsStateWithLifecycle()
    var previewMessage by remember { mutableStateOf<String?>(null) }
    var showAllVoices by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(output) { onDispose { output.close() } }

    ScreenFrame {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { WaveMark() }
        Text(strings.voiceTitle, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(strings.voiceIntro, style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream)
        VisionCard {
            Text(strings.language, style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VisionTextButton(strings.english, { onLanguageChange(UiLanguage.English) }, selected = language == UiLanguage.English)
                VisionTextButton(strings.hinglish, { onLanguageChange(UiLanguage.Hinglish) }, selected = language == UiLanguage.Hinglish)
            }
            Text(if (language == UiLanguage.English) strings.english else strings.hinglish, style = MaterialTheme.typography.bodyLarge)
        }
        VisionCard {
            Text(strings.speechSpeed, style = MaterialTheme.typography.titleLarge)
            Slider(
                modifier = Modifier.semantics { contentDescription = strings.speechSpeed },
                value = speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.7f..1.3f,
                colors = SliderDefaults.colors(
                    thumbColor = VisionColors.Cream,
                    activeTrackColor = VisionColors.Cream,
                    inactiveTrackColor = VisionColors.Outline,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.slower, style = MaterialTheme.typography.bodyMedium)
                Text(strings.faster, style = MaterialTheme.typography.bodyMedium)
            }
        }
        VisionButton(strings.testEnglish, onClick = {
            val attempt = output.preview(hinglish = false, speechRate = speechRate)
            previewMessage = if (attempt.queued) "${strings.voiceQueued} ${attempt.selectedVoice?.name} (${attempt.selectedVoice?.locale})" else strings.voiceUnavailable
        }, enabled = isReady)
        VisionButton(strings.testHinglish, onClick = {
            val attempt = output.preview(hinglish = true, speechRate = speechRate)
            previewMessage = if (attempt.queued) "${strings.voiceQueued} ${attempt.selectedVoice?.name} (${attempt.selectedVoice?.locale})" else strings.voiceUnavailable
        }, enabled = isReady)
        Text(previewMessage ?: if (isReady) strings.voiceInitial else strings.voiceLoading, style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
        VisionCard {
            Text(strings.voiceInventory, style = MaterialTheme.typography.titleLarge)
            Text(inventory.engine ?: "—", style = MaterialTheme.typography.bodyMedium)
            Text(strings.voiceOfflineNote, style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
            if (inventory.voices.isEmpty()) Text(strings.noOfflineVoices)
            (if (showAllVoices) inventory.voices else inventory.voices.take(3)).forEach { voice ->
                Text("${voice.locale}: ${voice.name} (quality ${voice.quality})", style = MaterialTheme.typography.bodyMedium)
            }
            if (inventory.voices.size > 3) {
                VisionTextButton(if (showAllVoices) strings.showFewerVoices else strings.showAllVoices, onClick = { showAllVoices = !showAllVoices })
            }
        }
        VisionButton(strings.continueLabel, onContinue)
    }
}
