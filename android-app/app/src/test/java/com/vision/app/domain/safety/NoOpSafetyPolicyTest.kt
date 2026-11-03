package com.vision.app.domain.safety

import com.vision.app.domain.vision.VisionObservation
import org.junit.Assert.assertNull
import org.junit.Test

class NoOpSafetyPolicyTest {
    @Test
    fun simulatedObservationNeverBecomesAWarning() {
        val observation = VisionObservation(
            description = "Sample text that sounds like a hazard",
            isSimulated = true,
        )

        assertNull(NoOpSafetyPolicy().warningFor(observation))
    }
}
