package com.vision.app.detector

/** Coordinates within CameraX's viewport crop, mapped into the unrotated RGBA plane. */
internal object UprightFrameMapping {
    fun sourceOffset(left: Int, top: Int, right: Int, bottom: Int,
        rotationDegrees: Int, uprightX: Int, uprightY: Int,
        rowStride: Int, pixelStride: Int): Int = when (rotationDegrees) {
        0 -> (top + uprightY) * rowStride + (left + uprightX) * pixelStride
        90 -> (bottom - 1 - uprightX) * rowStride + (left + uprightY) * pixelStride
        180 -> (bottom - 1 - uprightY) * rowStride + (right - 1 - uprightX) * pixelStride
        270 -> (top + uprightX) * rowStride + (right - 1 - uprightY) * pixelStride
        else -> throw IllegalArgumentException("Unsupported rotation: $rotationDegrees")
    }

    fun normalized(pixel: Float, size: Int): Float = (pixel / size).coerceIn(0f, 1f)
}
