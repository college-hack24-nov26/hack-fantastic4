package com.vision.app.domain.safety

import com.vision.app.domain.vision.VisionObservation

/** A real warning policy requires separately reviewed evidence and thresholds. */
interface SafetyPolicy {
    fun warningFor(observation: VisionObservation): String?
}

class NoOpSafetyPolicy : SafetyPolicy {
    override fun warningFor(observation: VisionObservation): String? = null
}
