package com.vision.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.vision.app.ui.VisionStrings
import com.vision.app.ui.components.ScreenFrame
import com.vision.app.ui.components.VisionButton
import com.vision.app.ui.components.VisionCard
import com.vision.app.ui.components.VisionTextButton
import com.vision.app.ui.theme.VisionColors

/** Preview-only screen. No ImageAnalysis or frame buffers are created here. */
@Composable
fun CameraPreviewScreen(strings: VisionStrings, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as Activity
    val requestHistory = remember(context) { context.getSharedPreferences("camera_permission_state", Activity.MODE_PRIVATE) }
    var requestedBefore by rememberSaveable { mutableStateOf(requestHistory.getBoolean("requested", false)) }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var cameraEnabled by rememberSaveable { mutableStateOf(true) }
    var streaming by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        cameraFailed = false
    }
    fun requestPermission() {
        requestedBefore = true
        requestHistory.edit().putBoolean("requested", true).apply()
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Entering this screen is the user's explicit activation of the live-camera feature.
    LaunchedEffect(Unit) {
        if (!permissionGranted && !requestedBefore) requestPermission()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
            if (event == Lifecycle.Event.ON_STOP) streaming = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permanentlyDenied = requestedBefore && !permissionGranted &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    ScreenFrame {
        VisionTextButton(strings.assistantBack, onBack)
        Text(strings.cameraTitle, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(strings.cameraPurpose, style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream)
        VisionCard {
            Text(strings.cameraOnly, style = MaterialTheme.typography.bodyMedium)
        }

        if (!permissionGranted) {
            Text(if (permanentlyDenied) strings.cameraPermissionBlocked else strings.cameraPermissionDenied)
            if (permanentlyDenied) {
                VisionButton(strings.openAppSettings, onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            } else {
                VisionButton(strings.requestCamera, onClick = ::requestPermission)
            }
        } else if (!cameraEnabled) {
            Text(strings.cameraStopped, style = MaterialTheme.typography.titleLarge)
            VisionButton(strings.startCamera, onClick = { cameraFailed = false; cameraEnabled = true })
        } else {
            Text(
                if (cameraFailed) strings.cameraUnavailable else if (streaming) strings.cameraActive else strings.cameraStarting,
                style = MaterialTheme.typography.titleLarge,
                color = VisionColors.Cream,
            )
            VisionButton(strings.stopCamera, onClick = { cameraEnabled = false })
            CameraPreview(
                description = strings.cameraTitle,
                onStreamState = { streaming = it },
                onFailure = { cameraFailed = true; streaming = false },
            )
        }
    }
}

@Composable
private fun CameraPreview(description: String, onStreamState: (Boolean) -> Unit, onFailure: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(480.dp).semantics { contentDescription = description },
    )
    DisposableEffect(previewView, lifecycleOwner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            onStreamState(state == PreviewView.StreamState.STREAMING)
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) {
                try {
                    provider = future.get()
                    val camera = provider!!.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    camera.cameraControl.setZoomRatio(1f)
                } catch (error: Exception) {
                    Log.e("VisionCamera", "Rear preview failed", error)
                    onFailure()
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            previewView.previewStreamState.removeObserver(streamObserver)
            provider?.unbind(preview)
        }
    }
}
