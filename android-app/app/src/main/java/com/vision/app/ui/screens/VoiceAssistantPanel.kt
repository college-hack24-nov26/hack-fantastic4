package com.vision.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.app.settings.UiLanguage
import com.vision.app.ui.VisionStrings
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.theme.VisionColors
import com.vision.app.voice.AssistantPhase
import com.vision.app.voice.VoiceAssistantSession

@Composable
fun VoiceAssistantPanel(
    strings: VisionStrings,
    language: UiLanguage,
    speechRate: Float,
    onLanguageChange: (UiLanguage) -> Unit,
    onRateChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(context) { VoiceAssistantSession(context) }
    val state by session.state.collectAsStateWithLifecycle()
    val history = remember(context) { context.getSharedPreferences("microphone_permission_state", Activity.MODE_PRIVATE) }
    var requestedBefore by rememberSaveable { mutableStateOf(history.getBoolean("requested", false)) }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    DisposableEffect(session) { onDispose { session.close() } }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }
            if (event == Lifecycle.Event.ON_STOP) session.stopListening()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) session.startListening(language, speechRate, onLanguageChange, onRateChange)
    }
    fun beginListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
            session.startListening(language, speechRate, onLanguageChange, onRateChange)
        } else {
            requestedBefore = true
            history.edit().putBoolean("requested", true).apply()
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val blocked = requestedBefore && !permissionGranted &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

    VisionCard {
        Text(strings.voiceAssistantTitle, style = MaterialTheme.typography.titleLarge)
        Text(strings.voiceAssistantNote, style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
        Text(
            when (state.phase) {
                AssistantPhase.Idle -> strings.voiceIdle
                AssistantPhase.Listening -> strings.voiceListening
                AssistantPhase.Processing -> strings.voiceProcessing
                AssistantPhase.Speaking -> strings.voiceSpeaking
                AssistantPhase.Error -> strings.voiceError
                AssistantPhase.Unavailable -> strings.offlineRecognizerMissing
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!session.onDeviceRecognitionAvailable) {
            Text(strings.offlineRecognizerMissing, style = MaterialTheme.typography.bodyMedium)
        } else {
            if (requestedBefore && !permissionGranted) {
                Text(if (blocked) strings.micBlocked else strings.micDenied, style = MaterialTheme.typography.bodyMedium)
                if (blocked) VisionTextButton(strings.openAppSettings, onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
            if (state.phase == AssistantPhase.Listening) {
                VisionButton(strings.stopListening, session::stopListening)
            } else if (!blocked) {
                VisionButton(if (permissionGranted) strings.startListening else strings.allowMicrophone, ::beginListening)
            }
            if (state.phase == AssistantPhase.Speaking) VisionTextButton(strings.stopSpeaking, session::interruptSpeech)
        }
        if (state.heard.isNotBlank()) {
            Text(strings.heardLabel, style = MaterialTheme.typography.titleLarge)
            Text(state.heard, style = MaterialTheme.typography.bodyLarge)
        }
        if (state.reply.isNotBlank()) {
            Text(strings.replyLabel, style = MaterialTheme.typography.titleLarge)
            Text(state.reply, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
