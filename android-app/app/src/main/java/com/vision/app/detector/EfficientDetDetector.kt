package com.vision.app.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.floor

/** CPU-only, synchronous IMAGE-mode detector; called only on the camera analysis worker. */
class EfficientDetDetector(private val context: Context) : AutoCloseable {
    private var task: ObjectDetector? = null
    private val pixels = IntArray(SIZE * SIZE)

    fun load() {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            context.assets.open(ASSET).use { source ->
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(chunk)
                    if (count < 0) break
                    digest.update(chunk, 0, count)
                    bytes += count
                }
            }
            val hash = digest.digest().joinToString("") { "%02X".format(it.toInt() and 0xff) }
            require(bytes == MODEL_BYTES && hash == MODEL_SHA256) { "EfficientDet model checksum mismatch" }
        } catch (error: FileNotFoundException) {
            throw FileNotFoundException("EfficientDet-Lite0 asset missing: $ASSET")
        }
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(12)
            .build()
        task = ObjectDetector.createFromOptions(context, options)
    }

    fun detect(image: ImageProxy, threshold: Float): List<Detection> {
        val active = requireNotNull(task) { "Detector is not loaded" }
        val bitmap = prepare(image)
        try {
            val result = active.detect(BitmapImageBuilder(bitmap).build())
            return result.detections().mapNotNull { found ->
                val category = found.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val box = found.boundingBox()
                val score = category.score()
                val label = category.categoryName().trim()
                if (!score.isFinite() || score < threshold || label.isBlank() || label == "???" ||
                    !box.left.isFinite() || !box.top.isFinite() || !box.right.isFinite() || !box.bottom.isFinite()) return@mapNotNull null
                val left = (box.left / SIZE).coerceIn(0f, 1f)
                val top = (box.top / SIZE).coerceIn(0f, 1f)
                val right = (box.right / SIZE).coerceIn(0f, 1f)
                val bottom = (box.bottom / SIZE).coerceIn(0f, 1f)
                if (right <= left || bottom <= top) null
                else Detection(category.index(), label, score, left, top, right, bottom)
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Map the common CameraX viewport crop upright into model space, including 90/270 rotation. */
    private fun prepare(image: ImageProxy): Bitmap {
        require(image.format == PixelFormat.RGBA_8888) { "Expected CameraX RGBA frame" }
        val plane = image.planes.single()
        val crop = image.cropRect
        val rotation = image.imageInfo.rotationDegrees
        require(rotation in listOf(0, 90, 180, 270))
        val width = if (rotation % 180 == 0) crop.width() else crop.height()
        val height = if (rotation % 180 == 0) crop.height() else crop.width()
        require(width > 0 && height > 0)
        val data = plane.buffer
        for (y in 0 until SIZE) {
            val fy = (y + 0.5f) * height / SIZE - 0.5f
            val y0 = floor(fy).toInt().coerceIn(0, height - 1)
            val y1 = (y0 + 1).coerceAtMost(height - 1)
            val wy = (fy - floor(fy)).coerceIn(0f, 1f)
            for (x in 0 until SIZE) {
                val fx = (x + 0.5f) * width / SIZE - 0.5f
                val x0 = floor(fx).toInt().coerceIn(0, width - 1)
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val wx = (fx - floor(fx)).coerceIn(0f, 1f)
                val r = blend(data, plane.rowStride, plane.pixelStride, crop.left, crop.top,
                    crop.right, crop.bottom, rotation, x0, x1, y0, y1, wx, wy, 0)
                val g = blend(data, plane.rowStride, plane.pixelStride, crop.left, crop.top,
                    crop.right, crop.bottom, rotation, x0, x1, y0, y1, wx, wy, 1)
                val b = blend(data, plane.rowStride, plane.pixelStride, crop.left, crop.top,
                    crop.right, crop.bottom, rotation, x0, x1, y0, y1, wx, wy, 2)
                pixels[y * SIZE + x] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun blend(data: ByteBuffer, rowStride: Int, pixelStride: Int,
        left: Int, top: Int, right: Int, bottom: Int, rotation: Int,
        x0: Int, x1: Int, y0: Int, y1: Int, wx: Float, wy: Float, channel: Int): Int {
        fun sample(x: Int, y: Int): Int {
            val rawX: Int
            val rawY: Int
            when (rotation) {
                90 -> { rawX = left + y; rawY = bottom - 1 - x }
                180 -> { rawX = right - 1 - x; rawY = bottom - 1 - y }
                270 -> { rawX = right - 1 - y; rawY = top + x }
                else -> { rawX = left + x; rawY = top + y }
            }
            return data.get(rawY * rowStride + rawX * pixelStride + channel).toInt() and 0xff
        }
        val upper = sample(x0, y0) * (1f - wx) + sample(x1, y0) * wx
        val lower = sample(x0, y1) * (1f - wx) + sample(x1, y1) * wx
        return (upper * (1f - wy) + lower * wy).toInt().coerceIn(0, 255)
    }

    override fun close() {
        task?.close()
        task = null
    }

    companion object {
        const val ASSET = "detector/efficientdet_lite0_int8.tflite"
        private const val MODEL_BYTES = 4_602_795L
        private const val MODEL_SHA256 = "0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB"
        private const val SIZE = 320
    }
}
