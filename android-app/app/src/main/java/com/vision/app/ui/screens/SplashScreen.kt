package com.vision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.WaveMark
import com.vision.app.ui.VisionStrings
import com.vision.app.ui.theme.VisionColors

@Composable
fun SplashScreen(strings: VisionStrings, onGetStarted: () -> Unit) {
    ScreenFrame {
        Spacer(Modifier.height(72.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WaveMark()
            Text("Vision", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.displayLarge)
            Text(strings.tagline, style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(64.dp))
        Text(
            strings.splashIntro,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            strings.splashNote,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = VisionColors.MutedCream,
            textAlign = TextAlign.Center,
        )
        VisionButton(strings.getStarted, onGetStarted)
    }
}
