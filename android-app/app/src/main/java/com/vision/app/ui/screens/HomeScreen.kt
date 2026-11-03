package com.vision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.WaveMark
import com.vision.app.ui.theme.VisionColors
import com.vision.app.ui.VisionStrings
import com.vision.app.settings.UiLanguage
import com.vision.app.ui.components.VisionTextButton

@Composable
fun HomeScreen(strings: VisionStrings, language: UiLanguage, onLanguageChange: (UiLanguage) -> Unit, onSettings: () -> Unit, onCamera: () -> Unit, onStart: () -> Unit) {
    ScreenFrame {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Vision", style = MaterialTheme.typography.headlineMedium)
            VisionTextButton(strings.settings, onSettings)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = VisionColors.Surface, contentColor = VisionColors.Cream) {
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) { WaveMark() }
            }
        }
        Text(strings.homeTitle, modifier = Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(
            strings.homeIntro,
            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = VisionColors.MutedCream,
        )
        VisionButton(strings.startDemo, onStart)
        VisionButton(strings.openCamera, onCamera)
        VisionCard {
            Text(strings.settings, style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VisionTextButton(strings.english, { onLanguageChange(UiLanguage.English) }, selected = language == UiLanguage.English)
                VisionTextButton(strings.hinglish, { onLanguageChange(UiLanguage.Hinglish) }, selected = language == UiLanguage.Hinglish)
            }
            Text(if (language == UiLanguage.English) strings.english else strings.hinglish)
            VisionTextButton(strings.voiceTitle, onSettings)
        }
        VisionCard {
            Text(strings.demoMode, style = MaterialTheme.typography.titleLarge)
            Text(strings.demoWarning, style = MaterialTheme.typography.bodyMedium)
        }
        VisionCard {
            Text(strings.comingLater, style = MaterialTheme.typography.titleLarge)
            Text(strings.comingLaterNote, style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
        }
    }
}
