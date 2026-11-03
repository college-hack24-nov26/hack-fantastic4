package com.vision.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.theme.VisionColors

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    ScreenFrame {
        Text("Permissions", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(
            "Vision will ask only for access needed by a feature when you choose to use it.",
            style = MaterialTheme.typography.bodyLarge,
            color = VisionColors.MutedCream,
        )
        VisionCard {
            Text("Camera", style = MaterialTheme.typography.titleLarge)
            Text("Future live view will need camera access. This build does not open the camera or request permission.", style = MaterialTheme.typography.bodyMedium)
        }
        VisionCard {
            Text("Microphone", style = MaterialTheme.typography.titleLarge)
            Text("Future voice commands may need microphone access. Voice preview uses speech output only.", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "You can continue without granting permissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = VisionColors.MutedCream,
        )
        VisionButton("Continue", onContinue)
    }
}
