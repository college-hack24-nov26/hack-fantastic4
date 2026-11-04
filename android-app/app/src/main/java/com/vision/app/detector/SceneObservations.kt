package com.vision.app.detector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HorizontalPosition { Left, Center, Right }

data class SceneObservation(
    val label: String,
    val score: Float,
    val box: Detection,
    val position: HorizontalPosition,
    val capturedAtMs: Long,
    val processedAtMs: Long,
    val matchesInLastThree: Int,
    val sessionId: Long,
    val current: Boolean,
) {
    val stable get() = matchesInLastThree >= 2
}

data class SceneSnapshot(
    val sessionId: Long? = null,
    val cameraActive: Boolean = false,
    val lastProcessedAtMs: Long? = null,
    val observations: List<SceneObservation> = emptyList(),
)

/** Small, synchronized detector-only history. No frame pixels or images are retained. */
class SceneObservations {
    private data class Frame(val detections: List<Detection>, val capturedAtMs: Long, val processedAtMs: Long)
    private val history = ArrayDeque<Frame>(3)
    private val mutableSnapshot = MutableStateFlow(SceneSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    @Synchronized fun start(sessionId: Long) {
        history.clear()
        mutableSnapshot.value = SceneSnapshot(sessionId = sessionId, cameraActive = true)
    }

    @Synchronized fun stop(sessionId: Long) {
        if (mutableSnapshot.value.sessionId != sessionId) return
        history.clear()
        mutableSnapshot.value = SceneSnapshot()
    }

    @Synchronized fun clear(sessionId: Long) {
        if (mutableSnapshot.value.sessionId != sessionId) return
        history.clear()
        mutableSnapshot.value = mutableSnapshot.value.copy(observations = emptyList(), lastProcessedAtMs = null)
    }

    @Synchronized fun publish(sessionId: Long, detections: List<Detection>, capturedAtMs: Long, processedAtMs: Long) {
        val current = mutableSnapshot.value
        if (!current.cameraActive || current.sessionId != sessionId) return
        history.addLast(Frame(detections.filter { it.score >= MIN_SCORE }.take(MAX_PER_FRAME), capturedAtMs, processedAtMs))
        while (history.size > 3) history.removeFirst()
        val last = history.last()
        val earlier = history.dropLast(1)
        val candidates = last.detections.map { detection ->
            SceneObservation(
                label = detection.label,
                score = detection.score,
                box = detection,
                position = position(detection),
                capturedAtMs = capturedAtMs,
                processedAtMs = processedAtMs,
                matchesInLastThree = 1 + earlier.count { frame -> frame.detections.any { matches(detection, it) } },
                sessionId = sessionId,
                current = true,
            )
        }.sortedByDescending { it.score }.take(MAX_PER_FRAME)
        mutableSnapshot.value = SceneSnapshot(sessionId, true, processedAtMs, candidates)
    }

    @Synchronized fun read(nowMs: Long): SceneSnapshot {
        val value = mutableSnapshot.value
        return value.copy(observations = value.observations.map { it.copy(current =
            value.cameraActive && it.sessionId == value.sessionId &&
                nowMs - it.capturedAtMs in 0..FRESHNESS_MS &&
                nowMs - it.processedAtMs in 0..FRESHNESS_MS) })
    }

    private fun matches(a: Detection, b: Detection): Boolean {
        if (a.classId != b.classId) return false
        val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
            (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        return intersection > 0f && intersection / (areaA + areaB - intersection) >= MIN_IOU
    }

    companion object {
        const val FRESHNESS_MS = 1_500L
        const val MIN_SCORE = 0.50f // Candidate filter, not a calibrated probability.
        private const val MIN_IOU = 0.25f
        private const val MAX_PER_FRAME = 12
        fun position(box: Detection): HorizontalPosition = when {
            (box.left + box.right) / 2f < 1f / 3f -> HorizontalPosition.Left
            (box.left + box.right) / 2f > 2f / 3f -> HorizontalPosition.Right
            else -> HorizontalPosition.Center
        }
    }
}
