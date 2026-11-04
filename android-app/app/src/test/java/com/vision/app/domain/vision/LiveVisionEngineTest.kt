package com.vision.app.domain.vision

import com.vision.app.detector.Detection
import com.vision.app.detector.SceneObservations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVisionEngineTest {
    private val chair = Detection(56, "chair", 0.8f, 0.2f, 0.3f, 0.6f, 0.9f)

    @Test fun resultsFlowThroughEngineAndClearOnStop() {
        val engine = LiveVisionEngine(SceneObservations())
        engine.start(1)
        engine.publish(1, listOf(chair), 100, 110)
        assertEquals(listOf(chair), engine.detections.value)
        assertEquals("chair", engine.scenes.snapshot.value.observations.single().label)
        engine.stop(1)
        assertTrue(engine.detections.value.isEmpty())
        assertFalse(engine.scenes.snapshot.value.cameraActive)
    }

    @Test fun staleSessionCannotRepopulateOverlay() {
        val engine = LiveVisionEngine(SceneObservations())
        engine.start(1)
        engine.start(2)
        engine.publish(1, listOf(chair), 100, 110)
        assertTrue(engine.detections.value.isEmpty())
        engine.publish(2, listOf(chair), 200, 210)
        engine.clear(2)
        assertTrue(engine.detections.value.isEmpty())
        assertTrue(engine.scenes.snapshot.value.observations.isEmpty())
    }
}
