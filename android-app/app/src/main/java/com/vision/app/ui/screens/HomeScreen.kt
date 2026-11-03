package com.vision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.WaveMark
import com.vision.app.ui.theme.VisionColors

@Composable
fun HomeScreen(onStart: () -> Unit) {
    ScreenFrame {
        Text("Vision", style = MaterialTheme.typography.headlineMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { WaveMark() }
        Text("Tap to start", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(
            "Explore the interface with sample observations. Camera and model inference are not active.",
            style = MaterialTheme.typography.bodyLarge,
            color = VisionColors.MutedCream,
        )
        VisionButton("Start demo assistant", onStart)
        VisionCard {
            Text("Demo mode", style = MaterialTheme.typography.titleLarge)
            Text("All observations are simulated. Do not use this build for navigation or safety decisions.", style = MaterialTheme.typography.bodyMedium)
        }
        VisionCard {
            Text("Coming later", style = MaterialTheme.typography.titleLarge)
            Text("Live view, route guidance, history, and emergency actions need separate implementation and review.", style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
        }
    }
}
