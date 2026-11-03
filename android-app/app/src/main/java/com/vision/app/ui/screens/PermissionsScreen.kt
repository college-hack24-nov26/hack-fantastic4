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
import com.vision.app.ui.VisionStrings

@Composable
fun PermissionsScreen(strings: VisionStrings, onContinue: () -> Unit) {
    ScreenFrame {
        Text(strings.permissions, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(
            strings.permissionsIntro,
            style = MaterialTheme.typography.bodyLarge,
            color = VisionColors.MutedCream,
        )
        VisionCard {
            Text(strings.camera, style = MaterialTheme.typography.titleLarge)
            Text(strings.cameraNote, style = MaterialTheme.typography.bodyMedium)
        }
        VisionCard {
            Text(strings.microphone, style = MaterialTheme.typography.titleLarge)
            Text(strings.microphoneNote, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            strings.permissionsEnd,
            style = MaterialTheme.typography.bodyMedium,
            color = VisionColors.MutedCream,
        )
        VisionButton(strings.continueLabel, onContinue)
    }
}
