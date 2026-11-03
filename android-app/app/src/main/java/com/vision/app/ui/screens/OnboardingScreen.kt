package com.vision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.StepDots
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.components.WaveMark
import com.vision.app.ui.theme.VisionColors

private data class OnboardingCopy(val title: String, val body: String, val detail: String)

private val pages = listOf(
    OnboardingCopy(
        "Carry Vision comfortably",
        "Keep your phone secure and its camera unobstructed when visual assistance becomes available.",
        "Camera setup and device support will be checked before use.",
    ),
    OnboardingCopy(
        "Hear useful context",
        "Vision is designed to speak concise observations and let you ask for more detail.",
        "Speech output can be previewed during setup.",
    ),
    OnboardingCopy(
        "Stay in control",
        "Use large touch controls and TalkBack throughout the experience.",
        "The current assistant screen shows sample content only.",
    ),
)

@Composable
fun OnboardingScreen(page: Int, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val copy = pages[page.coerceIn(pages.indices)]
    ScreenFrame {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisionTextButton("Back", onBack)
            VisionTextButton("Skip", onSkip)
        }
        StepDots(page, pages.size)
        Spacer(Modifier.height(36.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { WaveMark() }
        Spacer(Modifier.height(20.dp))
        Text(copy.title, modifier = Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(copy.body, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream, textAlign = TextAlign.Center)
        Text(copy.detail, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        VisionButton(if (page == pages.lastIndex) "Continue" else "Next", onNext)
    }
}
