package com.vision.app.domain.vision

/** Application-level observation, independent of any model tensor contract. */
data class VisionObservation(
    val description: String,
    val isSimulated: Boolean,
)

interface VisionEngine {
    suspend fun observe(): VisionObservation
}

class MockVisionEngine : VisionEngine {
    override suspend fun observe() = VisionObservation(
        description = "This is a sample observation for interface testing. No camera or model is active.",
        isSimulated = true,
    )
}
