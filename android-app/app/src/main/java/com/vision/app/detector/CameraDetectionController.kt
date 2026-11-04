package com.vision.app.detector

import android.content.Context
import android.os.SystemClock
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.FileNotFoundException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DetectorPhase { Loading, Running, Degraded, MissingModel, Error, Stopped }

data class DetectorState(
    val phase: DetectorPhase = DetectorPhase.Loading,
    val detections: List<Detection> = emptyList(),
    val inferenceMs: Double? = null,
    val frameMs: Double? = null,
    val captureAgeMs: Double? = null,
    val completedAtMs: Long = 0,
    val processed: Long = 0,
    val skipped: Long = 0,
    val estimatedDropped: Long = 0,
    val detail: String? = null,
    val power: PowerSnapshot? = null,
)

/** One foreground camera session, one analyzer worker, and one ONNX session. */
class CameraDetectionController(context: Context, private val scenes: SceneObservations) : ImageAnalysis.Analyzer, AutoCloseable {
    private val sessionId = nextSessionId.incrementAndGet()
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { job -> Thread(job, "vision-onnx-analysis") }
    private val stopped = AtomicBoolean(false)
    private var detector: OnnxObjectDetector? = null
    private val preprocessor = CameraFramePreprocessor()
    private val powerMonitor = PowerThermalMonitor(appContext)
    private val mutableState = MutableStateFlow(DetectorState())
    val state = mutableState.asStateFlow()

    @Volatile var confidenceThreshold = 0.25f
    private var lastStartNs = 0L
    private var lastCaptureNs = 0L
    private var processed = 0L
    private var skipped = 0L
    private var estimatedDropped = 0L

    val executor get() = worker

    fun start() {
        scenes.start(sessionId)
        worker.execute {
            if (stopped.get()) return@execute
            try {
                val loaded = OnnxObjectDetector(appContext).also { it.load() }
                if (stopped.get()) {
                    loaded.close()
                    return@execute
                }
                detector = loaded
                mutableState.value = mutableState.value.copy(phase = DetectorPhase.Running)
                Log.i(TAG, "model loaded; CPU inference ready")
            } catch (error: Throwable) {
                Log.e(TAG, "model load failed", error)
                scenes.stop(sessionId)
                if (!stopped.get()) mutableState.value = DetectorState(
                    phase = if (error is FileNotFoundException) DetectorPhase.MissingModel else DetectorPhase.Error,
                    detail = error.javaClass.simpleName)
            }
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (stopped.get()) return
            val timestampNs = image.imageInfo.timestamp
            if (lastCaptureNs > 0 && timestampNs > lastCaptureNs) {
                estimatedDropped += ((timestampNs - lastCaptureNs) / 33_333_333L - 1).coerceAtLeast(0)
            }
            lastCaptureNs = timestampNs
            val nowNs = SystemClock.elapsedRealtimeNanos()
            val active = detector
            if (active == null || nowNs - lastStartNs < MIN_INTERVAL_NS) {
                skipped++
                return
            }
            lastStartNs = nowNs
            val power = powerMonitor.snapshot()
            if ((power.thermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE) {
                scenes.clear(sessionId)
                skipped++
                mutableState.value = mutableState.value.copy(phase = DetectorPhase.Degraded,
                    detections = emptyList(), skipped = skipped, power = power,
                    detail = "Android reports thermal throttling")
                return
            }
            val tensor = preprocessor.prepare(image)
            val inferenceStart = SystemClock.elapsedRealtimeNanos()
            val boxes = active.detect(tensor, confidenceThreshold)
            val end = SystemClock.elapsedRealtimeNanos()
            if (stopped.get()) return
            processed++
            val frameMs = (end - nowNs) / 1_000_000.0
            val captureAge = (end - timestampNs) / 1_000_000.0
            val age = captureAge.takeIf { it in 0.0..10_000.0 }
            val degraded = frameMs > MAX_FRAME_MS || (age != null && age > MAX_CAPTURE_AGE_MS)
            if (degraded) scenes.clear(sessionId)
            else scenes.publish(sessionId, boxes,
                capturedAtMs = age?.let { SystemClock.elapsedRealtime() - it.toLong() } ?: SystemClock.elapsedRealtime(),
                processedAtMs = SystemClock.elapsedRealtime())
            mutableState.value = DetectorState(
                phase = if (degraded) DetectorPhase.Degraded else DetectorPhase.Running,
                detections = if (degraded) emptyList() else boxes,
                inferenceMs = (end - inferenceStart) / 1_000_000.0,
                frameMs = frameMs,
                captureAgeMs = age,
                completedAtMs = SystemClock.elapsedRealtime(),
                processed = processed,
                skipped = skipped,
                estimatedDropped = estimatedDropped,
                detail = if (degraded) "Frame processing exceeded the monitoring limit" else null,
                power = power,
            )
            if (processed <= 3 || processed % 20L == 0L) {
                Log.i(TAG, "frame=$processed inferenceMs=${(end - inferenceStart) / 1_000_000.0} " +
                    "frameMs=$frameMs captureAgeMs=$age detections=${boxes.size} " +
                    "top=${boxes.take(3).joinToString { "${it.label}:${"%.2f".format(it.score)}" }}")
            }
        } catch (error: Throwable) {
            Log.e(TAG, "analysis failed", error)
            scenes.stop(sessionId)
            if (!stopped.get()) mutableState.value = mutableState.value.copy(phase = DetectorPhase.Error,
                detections = emptyList(), detail = error.javaClass.simpleName)
            try { detector?.close() } catch (closeError: Throwable) { Log.e(TAG, "detector close failed", closeError) }
            detector = null
        } finally {
            image.close()
        }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        scenes.stop(sessionId)
        mutableState.value = mutableState.value.copy(phase = DetectorPhase.Stopped, detections = emptyList())
        worker.execute {
            try { detector?.close() } catch (error: Throwable) { Log.e(TAG, "detector close failed", error) }
            detector = null
            mutableState.value = mutableState.value.copy(phase = DetectorPhase.Stopped, detections = emptyList())
            Log.i(TAG, "detector session released; analysis worker stopping")
        }
        worker.shutdown()
    }

    companion object {
        private const val TAG = "VisionDetector"
        private val nextSessionId = AtomicLong()
        private const val MIN_INTERVAL_NS = 500_000_000L // 2 Hz after Samsung measured ~200 ms full-frame processing.
        private const val MAX_FRAME_MS = 750.0
        private const val MAX_CAPTURE_AGE_MS = 1_000.0
    }
}
