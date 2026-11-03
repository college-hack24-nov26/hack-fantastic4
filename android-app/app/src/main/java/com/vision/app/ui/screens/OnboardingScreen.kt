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
import com.vision.app.ui.VisionStrings

@Composable
fun OnboardingScreen(page: Int, strings: VisionStrings, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val copy = strings.onboarding[page.coerceIn(strings.onboarding.indices)]
    ScreenFrame {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisionTextButton(strings.back, onBack)
            VisionTextButton(strings.skip, onSkip)
        }
        StepDots(page, strings.onboarding.size, strings.stepOf(page + 1, strings.onboarding.size))
        Spacer(Modifier.height(36.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { WaveMark() }
        Spacer(Modifier.height(20.dp))
        Text(copy.first, modifier = Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(copy.second, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream, textAlign = TextAlign.Center)
        Text(copy.third, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        VisionButton(if (page == strings.onboarding.lastIndex) strings.continueLabel else strings.next, onNext)
    }
}
