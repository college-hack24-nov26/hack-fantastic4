package com.vision.app.detector

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.floor

/** CameraX RGBA plane to the ML team's bilinear RGB CHW [0,1] input. */
class CameraFramePreprocessor {
    private val output: FloatBuffer = ByteBuffer.allocateDirect(3 * SIZE * SIZE * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun prepare(image: ImageProxy): FloatBuffer {
        require(image.format == android.graphics.PixelFormat.RGBA_8888) { "Expected RGBA CameraX frame" }
        val plane = image.planes.single()
        val data = plane.buffer
        val crop = image.cropRect
        val rotation = image.imageInfo.rotationDegrees
        require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270)
        val width = if (rotation % 180 == 0) crop.width() else crop.height()
        val height = if (rotation % 180 == 0) crop.height() else crop.width()
        require(width > 0 && height > 0)
        for (y in 0 until SIZE) {
            val sourceY = (y + 0.5f) * height / SIZE - 0.5f
            val y0 = floor(sourceY).toInt()
            val y1 = y0 + 1
            val wy = sourceY - y0
            for (x in 0 until SIZE) {
                val sourceX = (x + 0.5f) * width / SIZE - 0.5f
                val x0 = floor(sourceX).toInt()
                val x1 = x0 + 1
                val wx = sourceX - x0
                val offset = y * SIZE + x
                for (channel in 0..2) {
                    val a = sample(data, plane.rowStride, plane.pixelStride, crop, rotation, x0.coerceIn(0, width - 1), y0.coerceIn(0, height - 1), channel)
                    val b = sample(data, plane.rowStride, plane.pixelStride, crop, rotation, x1.coerceIn(0, width - 1), y0.coerceIn(0, height - 1), channel)
                    val c = sample(data, plane.rowStride, plane.pixelStride, crop, rotation, x0.coerceIn(0, width - 1), y1.coerceIn(0, height - 1), channel)
                    val d = sample(data, plane.rowStride, plane.pixelStride, crop, rotation, x1.coerceIn(0, width - 1), y1.coerceIn(0, height - 1), channel)
                    val upper = a * (1 - wx) + b * wx
                    val lower = c * (1 - wx) + d * wx
                    output.put(channel * SIZE * SIZE + offset, (upper * (1 - wy) + lower * wy) / 255f)
                }
            }
        }
        output.rewind()
        return output
    }

    private fun sample(data: ByteBuffer, rowStride: Int, pixelStride: Int, crop: Rect,
        rotation: Int, x: Int, y: Int, channel: Int): Int {
        val rawX: Int
        val rawY: Int
        when (rotation) {
            90 -> { rawX = crop.left + y; rawY = crop.bottom - 1 - x }
            180 -> { rawX = crop.right - 1 - x; rawY = crop.bottom - 1 - y }
            270 -> { rawX = crop.right - 1 - y; rawY = crop.top + x }
            else -> { rawX = crop.left + x; rawY = crop.top + y }
        }
        return data.get(rawY * rowStride + rawX * pixelStride + channel).toInt() and 0xff
    }

    companion object { private const val SIZE = 320 }
}
