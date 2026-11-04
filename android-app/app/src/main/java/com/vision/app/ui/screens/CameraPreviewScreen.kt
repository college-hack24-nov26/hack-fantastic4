package com.vision.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.app.detector.CameraDetectionController
import com.vision.app.detector.Detection
import com.vision.app.detector.DetectorPhase
import com.vision.app.detector.SceneObservations
import com.vision.app.voice.VoiceAssistantSession
import com.vision.app.domain.voice.CameraRuntimeStatus
import com.vision.app.settings.UiLanguage
import com.vision.app.ui.VisionStrings
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.theme.VisionColors
import java.util.Locale
import kotlinx.coroutines.delay

/** Experimental detector display. Scene descriptions remain separate from safety policy. */
@Composable
fun CameraPreviewScreen(strings: VisionStrings, language: UiLanguage, speechRate: Float,
    scenes: SceneObservations, automaticAnnouncements: Boolean,
    onAutomaticAnnouncementsChange: (Boolean) -> Unit,
    onLanguageChange: (UiLanguage) -> Unit, onRateChange: (Float) -> Unit,
    voiceSession: VoiceAssistantSession, cameraEnabled: Boolean,
    cameraStatus: CameraRuntimeStatus,
    onStartCamera: () -> Unit, onStopCamera: () -> Unit,
    onCameraStarted: () -> Unit, onCameraStopped: () -> Unit, onCameraFailure: () -> Unit,
    onBack: () -> Unit) {
    val copy = DetectorCopy.forLanguage(language)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as Activity
    val requestHistory = remember(context) { context.getSharedPreferences("camera_permission_state", Activity.MODE_PRIVATE) }
    var requestedBefore by rememberSaveable { mutableStateOf(requestHistory.getBoolean("requested", false)) }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var streaming by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        cameraFailed = false
        if (!granted) onCameraFailure()
    }
    fun requestPermission() {
        requestedBefore = true
        requestHistory.edit().putBoolean("requested", true).apply()
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (!permissionGranted && !requestedBefore) requestPermission() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (event == Lifecycle.Event.ON_STOP) streaming = false // CameraX pauses with the lifecycle.
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permanentlyDenied = requestedBefore && !permissionGranted &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    DisposableEffect(Unit) { onDispose { onCameraStopped() } }
    ScreenFrame {
        VisionTextButton(strings.assistantBack, onBack)
        Text(strings.cameraTitle, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(copy.warning, style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream)
        if (!permissionGranted) {
            Text(if (permanentlyDenied) strings.cameraPermissionBlocked else strings.cameraPermissionDenied)
            if (permanentlyDenied) VisionButton(strings.openAppSettings, onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) else VisionButton(strings.requestCamera, onClick = ::requestPermission)
        } else if (!cameraEnabled) {
            Text(if (cameraFailed) strings.cameraUnavailable else strings.cameraStopped, style = MaterialTheme.typography.titleLarge)
            VisionButton(strings.startCamera, onClick = { cameraFailed = false; onStartCamera() })
        } else {
            Text(if (streaming) strings.cameraActive else strings.cameraStarting,
                style = MaterialTheme.typography.titleLarge, color = VisionColors.Cream)
            VisionButton(strings.stopCamera, onClick = { onStopCamera(); streaming = false })
            CameraMonitor(strings.cameraTitle, copy, scenes,
                onStreamState = { streaming = it; if (it) onCameraStarted() },
                onFailure = { cameraFailed = true; onCameraFailure(); streaming = false },
                onStopped = onCameraStopped)
        }
        VoiceAssistantPanel(strings, language, speechRate, onLanguageChange, onRateChange,
            scenes, automaticAnnouncements, onAutomaticAnnouncementsChange, voiceSession, cameraStatus)
    }
}

@Composable
private fun CameraMonitor(description: String, copy: DetectorCopy, scenes: SceneObservations,
    onStreamState: (Boolean) -> Unit, onFailure: () -> Unit, onStopped: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val controller = remember(context) { CameraDetectionController(context, scenes) }
    val state by controller.state.collectAsStateWithLifecycle()
    var threshold by rememberSaveable { mutableStateOf(0.25f) }
    controller.confidenceThreshold = threshold
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { nowMs = SystemClock.elapsedRealtime(); delay(500) } }
    val resultAge = if (state.completedAtMs > 0) (nowMs - state.completedAtMs).coerceAtLeast(0L) else null
    val stale = resultAge != null && resultAge > 1_500L
    val visible = if (!stale && state.phase == DetectorPhase.Running) state.detections.take(12) else emptyList()

    BoxWithConstraints(Modifier.fillMaxWidth().height(480.dp).semantics {
        contentDescription = if (visible.isEmpty()) "$description. ${copy.noReliableResult}"
        else "$description. ${copy.experimentalGuesses}: " + visible.take(5).joinToString { "${it.label} ${percent(it.score)}" }
    }) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            visible.forEach { box ->
                drawRect(VisionColors.Cream,
                    topLeft = Offset(box.left * size.width, box.top * size.height),
                    size = Size((box.right - box.left) * size.width, (box.bottom - box.top) * size.height),
                    style = Stroke(width = 2.dp.toPx()))
            }
        }
        visible.take(8).forEach { box ->
            Text("${copy.guessPrefix} ${copy.label(box)} ${percent(box.score)}",
                modifier = Modifier.offset(x = maxWidth * box.left, y = maxHeight * box.top)
                    .background(VisionColors.Cream).padding(horizontal = 3.dp),
                color = VisionColors.CharcoalText, style = MaterialTheme.typography.bodyMedium)
        }
    }
    VisionCard {
        val phase = if (stale && state.phase == DetectorPhase.Running) DetectorPhase.Degraded else state.phase
        Text("${copy.status}: ${copy.phase(phase)}", style = MaterialTheme.typography.titleLarge)
        if (state.detail != null) Text("${copy.detail}: ${state.detail}", style = MaterialTheme.typography.bodyMedium)
        Text("${copy.threshold}: ${percent(threshold)}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.05f..0.8f,
            modifier = Modifier.semantics { contentDescription = copy.threshold })
        Text("${copy.inference}: ${state.inferenceMs?.formatMs() ?: "—"} · ${copy.frame}: ${state.frameMs?.formatMs() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium)
        Text("${copy.age}: ${resultAge?.let { "$it ms" } ?: "—"} · ${copy.captureAge}: ${state.captureAgeMs?.formatMs() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium)
        Text("${copy.processed}: ${state.processed} · ${copy.skipped}: ${state.skipped} · ${copy.estimatedDropped}: ${state.estimatedDropped}",
            style = MaterialTheme.typography.bodyMedium)
        state.power?.let { power ->
            Text("${copy.thermal}: ${power.thermalStatus ?: "—"} · ${copy.battery}: ${power.batteryPercent?.let { "$it%" } ?: "—"} · ${copy.charging}: ${power.charging} · ${copy.saver}: ${power.batterySaver}",
                style = MaterialTheme.typography.bodyMedium)
        }
        Text(copy.metricsNote, style = MaterialTheme.typography.bodyMedium, color = VisionColors.MutedCream)
    }

    DisposableEffect(previewView, lifecycleOwner, controller) {
        var disposed = false
        var bound = false
        var provider: ProcessCameraProvider? = null
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
        controller.start()
        analysis.setAnalyzer(controller.executor, controller)
        val observer = Observer<PreviewView.StreamState> { onStreamState(it == PreviewView.StreamState.STREAMING) }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        val future = ProcessCameraProvider.getInstance(context)
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!disposed && !bound && previewView.width > 0 && previewView.height > 0 && previewView.viewPort != null) {
                bound = true
                future.addListener({
                    if (!disposed) try {
                        provider = future.get()
                        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
                            .setViewPort(requireNotNull(previewView.viewPort)).build()
                        val camera = provider!!.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                        camera.cameraControl.setZoomRatio(1f)
                    } catch (error: Exception) {
                        Log.e("VisionCamera", "Rear preview and analysis failed", error)
                        onFailure()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
        previewView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        onDispose {
            disposed = true
            previewView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            previewView.previewStreamState.removeObserver(observer)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            controller.close()
            onStopped()
        }
    }
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
private fun Double.formatMs(): String = String.format(Locale.US, "%.1f ms", this)

private data class DetectorCopy(
    val warning: String, val status: String, val threshold: String, val inference: String,
    val frame: String, val age: String, val captureAge: String, val processed: String,
    val skipped: String, val estimatedDropped: String, val detail: String,
    val metricsNote: String, val noReliableResult: String, val experimentalGuesses: String,
    val thermal: String, val battery: String, val charging: String, val saver: String, val guessPrefix: String,
    val isEnglish: Boolean,
) {
    fun label(detection: Detection) = if (isEnglish) detection.label else when (detection.label) {
        "person" -> "insaan"; "bicycle" -> "cycle"; "chair" -> "kursi"; else -> detection.label
    }
    fun phase(phase: DetectorPhase): String = when (phase) {
        DetectorPhase.Loading -> if (isEnglish) "Loading model" else "Model load ho raha hai"
        DetectorPhase.Running -> if (isEnglish) "Experimental detection active" else "Experimental detection chal rahi hai"
        DetectorPhase.Degraded -> if (isEnglish) "Monitoring degraded; results hidden" else "Monitoring kamzor hai; results chhupaye hain"
        DetectorPhase.MissingModel -> if (isEnglish) "Model missing; preview only" else "Model nahi mila; sirf preview"
        DetectorPhase.Error -> if (isEnglish) "Detector failed; preview only" else "Detector fail hua; sirf preview"
        DetectorPhase.Stopped -> if (isEnglish) "Stopped" else "Band hai"
    }
    companion object {
        fun forLanguage(language: UiLanguage) = if (language == UiLanguage.English) DetectorCopy(
            warning = "EXPERIMENTAL object guesses. This model misses objects and makes false detections. Never use it for walking or safety decisions.",
            status = "Detector", threshold = "Minimum score (not a safety confidence)", inference = "Conversion + inference", frame = "Frame",
            age = "Result age", captureAge = "Capture age", processed = "Processed", skipped = "Cadence skipped",
            estimatedDropped = "Est. camera drops", detail = "Reason", metricsNote = "Camera drops are estimated from frame timestamps; frames are not saved.",
            noReliableResult = "No reliable object information", experimentalGuesses = "Experimental guesses",
            thermal = "Thermal status", battery = "Battery", charging = "Charging", saver = "Battery saver",
            guessPrefix = "guess:",
            isEnglish = true,
        ) else DetectorCopy(
            warning = "EXPERIMENTAL object guesses. Model kuch cheezein miss karta hai aur galat bhi batata hai. Chalne ya safety decision ke liye use na karein.",
            status = "Detector", threshold = "Minimum score (safety confidence nahi)", inference = "Conversion + inference", frame = "Frame",
            age = "Result ki age", captureAge = "Capture age", processed = "Process hue", skipped = "Cadence se skip",
            estimatedDropped = "Est. camera drops", detail = "Wajah", metricsNote = "Camera drops timestamp se estimate hain; frames save nahi hote.",
            noReliableResult = "Bharosemand object info nahi hai", experimentalGuesses = "Experimental guesses",
            thermal = "Thermal status", battery = "Battery", charging = "Charging", saver = "Battery saver",
            guessPrefix = "andaza:",
            isEnglish = false,
        )
    }
}
