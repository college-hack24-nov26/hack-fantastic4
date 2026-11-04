package com.vision.app.detector

import org.junit.Assert.assertEquals
import org.junit.Test

class UprightFrameMappingTest {
    @Test fun viewportCropCornersRotateIntoSourcePlane() {
        val expected = mapOf(
            0 to ((20 * 100 + 10 * 4) to (22 * 100 + 13 * 4)),
            90 to ((22 * 100 + 10 * 4) to (20 * 100 + 13 * 4)),
            180 to ((22 * 100 + 13 * 4) to (20 * 100 + 10 * 4)),
            270 to ((20 * 100 + 13 * 4) to (22 * 100 + 10 * 4)),
        )
        expected.forEach { (rotation, corners) ->
            val width = if (rotation % 180 == 0) 4 else 3
            val height = if (rotation % 180 == 0) 3 else 4
            assertEquals(corners.first, UprightFrameMapping.sourceOffset(10, 20, 14, 23,
                rotation, 0, 0, 100, 4))
            assertEquals(corners.second, UprightFrameMapping.sourceOffset(10, 20, 14, 23,
                rotation, width - 1, height - 1, 100, 4))
        }
    }

    @Test fun modelBoxCoordinatesNormalizeForEitherPreviewAspectRatio() {
        val left = UprightFrameMapping.normalized(80f, 320)
        val right = UprightFrameMapping.normalized(240f, 320)
        assertEquals(0.25f, left, 0.0001f)
        assertEquals(0.75f, right, 0.0001f)
        assertEquals(270f, left * 1080f, 0.01f)
        assertEquals(1800f, right * 2400f, 0.01f)
    }
}
