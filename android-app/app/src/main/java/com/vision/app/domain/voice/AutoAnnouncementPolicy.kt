package com.vision.app.domain.voice

import com.vision.app.detector.SceneObservations
import com.vision.app.detector.SceneSnapshot

/** Drop unchanged or obsolete candidates; never build a speech queue. */
class AutoAnnouncementPolicy {
    private var sessionId: Long? = null
    private var lastKey: String? = null
    private var lastAtMs = Long.MIN_VALUE

    fun candidate(snapshot: SceneSnapshot, nowMs: Long): String? {
        if (!snapshot.cameraActive || snapshot.sessionId == null) { reset(); return null }
        if (sessionId != snapshot.sessionId) { reset(); sessionId = snapshot.sessionId }
        val matches = snapshot.observations.filter {
            it.current && it.stable && it.score >= SceneObservations.MIN_SCORE &&
                nowMs - it.capturedAtMs in 0..SceneObservations.FRESHNESS_MS
        }.take(2)
        if (matches.isEmpty()) { lastKey = null; return null }
        val key = matches.map { "${it.label}:${it.position}" }.sorted().joinToString("|")
        if (key == lastKey || (lastAtMs != Long.MIN_VALUE && nowMs - lastAtMs < COOLDOWN_MS)) return null
        return key
    }

    fun markAnnounced(key: String, nowMs: Long) { lastKey = key; lastAtMs = nowMs }

    fun reset() { sessionId = null; lastKey = null; lastAtMs = Long.MIN_VALUE }

    companion object { const val COOLDOWN_MS = 8_000L }
}
