package com.vision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.components.WaveMark
import com.vision.app.ui.theme.VisionColors
import com.vision.app.voice.AndroidVoiceOutput

@Composable
fun VoiceSetupScreen(
    languageTag: String,
    speechRate: Float,
    onLanguageChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val output = remember(context) { AndroidVoiceOutput(context) }
    val isReady by output.ready.collectAsStateWithLifecycle()
    var previewMessage by remember { mutableStateOf("Voice preview uses an installed offline voice when available.") }
    DisposableEffect(output) { onDispose { output.close() } }

    ScreenFrame {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { WaveMark() }
        Text("Set up your voice", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text("Choose a voice preview that feels comfortable.", style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream)
        VisionCard {
            Text("Language", style = MaterialTheme.typography.titleLarge)
            Text(if (languageTag == "en-IN") "English (India)" else "English (United States)", style = MaterialTheme.typography.bodyLarge)
            VisionTextButton("Change language", onClick = {
                onLanguageChange(if (languageTag == "en-IN") "en-US" else "en-IN")
            })
        }
        VisionCard {
            Text("Speech speed", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.7f..1.3f,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Slower", style = MaterialTheme.typography.bodyMedium)
                Text("Faster", style = MaterialTheme.typography.bodyMedium)
            }
        }
        VisionButton("Test voice", onClick = {
            previewMessage = if (output.preview(languageTag, speechRate)) "Playing offline voice preview." else "An offline voice for this language is unavailable on this device."
        }, enabled = isReady)
        Text(previewMessage, style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
        VisionButton("Continue", onContinue)
    }
}
