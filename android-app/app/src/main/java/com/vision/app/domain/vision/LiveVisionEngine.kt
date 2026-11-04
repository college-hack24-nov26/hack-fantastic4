package com.vision.app.domain.vision

import android.os.SystemClock
import com.vision.app.detector.Detection
import com.vision.app.detector.SceneObservations
import com.vision.app.settings.UiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground camera observations shared by the overlay, voice descriptions, and assistant. */
class LiveVisionEngine(val scenes: SceneObservations) : VisionEngine {
    private val mutableDetections = MutableStateFlow<List<Detection>>(emptyList())
    val detections = mutableDetections.asStateFlow()

    @Synchronized fun start(sessionId: Long) {
        mutableDetections.value = emptyList()
        scenes.start(sessionId)
    }

    @Synchronized fun publish(sessionId: Long, detections: List<Detection>, capturedAtMs: Long, processedAtMs: Long) {
        if (scenes.snapshot.value.sessionId != sessionId || !scenes.snapshot.value.cameraActive) return
        scenes.publish(sessionId, detections, capturedAtMs, processedAtMs)
        mutableDetections.value = detections
    }

    @Synchronized fun clear(sessionId: Long) {
        if (scenes.snapshot.value.sessionId != sessionId) return
        mutableDetections.value = emptyList()
        scenes.clear(sessionId)
    }

    @Synchronized fun stop(sessionId: Long) {
        if (scenes.snapshot.value.sessionId != sessionId) return
        mutableDetections.value = emptyList()
        scenes.stop(sessionId)
    }

    override suspend fun observe(language: UiLanguage): VisionObservation {
        val now = SystemClock.elapsedRealtime()
        val scene = scenes.read(now)
        val hinglish = language == UiLanguage.Hinglish
        val labels = scene.observations.filter { it.current && it.stable && it.score >= SceneObservations.MIN_SCORE }
            .take(2).map { it.label }
        val description = when {
            !scene.cameraActive -> if (hinglish) "Camera monitoring band hai. Object description ke liye camera shuru karein."
                else "Camera monitoring is off. Start the camera for experimental object descriptions."
            scene.lastProcessedAtMs == null || now - scene.lastProcessedAtMs !in 0..SceneObservations.FRESHNESS_MS ->
                if (hinglish) "Abhi recent camera result nahi hai." else "No recent camera result is available."
            labels.isEmpty() -> if (hinglish) "Camera abhi koi object confidently identify nahi kar pa raha."
                else "The camera has not identified an object confidently."
            hinglish -> "Camera view mein shayad ${labels.joinToString(" aur ") { when (it) {
                "person" -> "insaan"; "bicycle" -> "cycle"; "chair" -> "kursi"; else -> it
            } }} hai. Yeh experimental guess hai."
            else -> "Possible ${labels.joinToString(" and ")} in the camera view. This is an experimental guess."
        }
        return VisionObservation(description, isSimulated = false)
    }
}
