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

@Composable
fun ActiveAssistantScreen(
    observation: VisionObservation?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenFrame {
        VisionTextButton("Back to home", onBack)
        Text("Demo assistant", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        VisionCard {
            Text("SIMULATED CONTENT", style = MaterialTheme.typography.titleLarge, color = VisionColors.Cream)
            Text(
                "No camera, detection model, or safety warning system is running.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("Sample observation", style = MaterialTheme.typography.titleLarge)
        Text(
            if (isLoading) "Loading sample…" else observation?.description ?: "Tap the button to load a sample observation.",
            style = MaterialTheme.typography.bodyLarge,
            color = VisionColors.MutedCream,
        )
        VisionButton("Refresh sample", onRefresh, enabled = !isLoading)
        Text(
            "This demo cannot judge whether an obstacle or route is safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionColors.MutedCream,
        )
    }
}
