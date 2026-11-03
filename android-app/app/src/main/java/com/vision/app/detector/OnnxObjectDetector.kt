package com.vision.app.detector

import android.content.Context
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

/** Experimental detector. It never produces a safety decision or spoken warning. */
class OnnxObjectDetector(private val context: Context) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    fun load() {
        val model = verifiedModelFile()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            val loaded = environment.createSession(model.absolutePath, options)
            try {
                val input = loaded.inputInfo["rgb_input"]?.info as? TensorInfo
                val boxes = loaded.outputInfo["boxes"]?.info as? TensorInfo
                val scores = loaded.outputInfo["scores"]?.info as? TensorInfo
                val labels = loaded.outputInfo["labels"]?.info as? TensorInfo
                require(input?.type == OnnxJavaType.FLOAT && input.shape.contentEquals(longArrayOf(1, 3, 320, 320)))
                require(boxes?.type == OnnxJavaType.FLOAT && boxes.shape.size == 2 && boxes.shape[1] == 4L)
                require(scores?.type == OnnxJavaType.FLOAT && scores.shape.size == 1)
                require(labels?.type == OnnxJavaType.INT64 && labels.shape.size == 1)
                session = loaded
            } catch (error: Exception) {
                loaded.close()
                throw error
            }
        } finally {
            options.close()
        }
    }

    fun detect(rgbChw: FloatBuffer, threshold: Float): List<Detection> {
        val active = requireNotNull(session) { "Detector is not loaded" }
        rgbChw.rewind()
        OnnxTensor.createTensor(environment, rgbChw, longArrayOf(1, 3, 320, 320)).use { input ->
            active.run(mapOf("rgb_input" to input)).use { output ->
                val boxes = (output.get("boxes").orElseThrow() as OnnxTensor).value as Array<FloatArray>
                val scores = (output.get("scores").orElseThrow() as OnnxTensor).value as FloatArray
                val labels = (output.get("labels").orElseThrow() as OnnxTensor).value as LongArray
                require(boxes.size == scores.size && scores.size == labels.size && scores.size <= 300)
                return scores.indices.mapNotNull { index ->
                    val box = boxes[index]
                    val score = scores[index]
                    val label = labels[index].toInt()
                    if (box.size != 4 || !score.isFinite() || label !in 1..5 || box.any { !it.isFinite() }) {
                        throw IllegalStateException("Invalid detector output at $index")
                    }
                    if (score < threshold || box[0] >= box[2] || box[1] >= box[3]) null
                    else Detection(label, CLASS_NAMES[label]!!, score,
                        (box[0] / 320f).coerceIn(0f, 1f), (box[1] / 320f).coerceIn(0f, 1f),
                        (box[2] / 320f).coerceIn(0f, 1f), (box[3] / 320f).coerceIn(0f, 1f))
                }
            }
        }
    }

    private fun verifiedModelFile(): File {
        val directory = File(context.filesDir, "detector").apply { mkdirs() }
        val model = File(directory, "ssdlite_phase2b_source.onnx")
        if (model.isFile && model.length() == MODEL_BYTES && sha256(model) == MODEL_SHA256) return model
        val temporary = File(directory, "model.importing")
        try {
            context.assets.open("detector/model.onnx").use { source ->
                temporary.outputStream().use { target -> source.copyTo(target) }
            }
            require(temporary.length() == MODEL_BYTES && sha256(temporary) == MODEL_SHA256) { "Detector model checksum mismatch" }
            if (model.exists()) require(model.delete()) { "Could not replace detector model" }
            require(temporary.renameTo(model)) { "Could not install detector model" }
            return model
        } finally {
            temporary.delete()
        }
    }

    override fun close() {
        val closing = session
        session = null
        closing?.close()
    }

    companion object {
        private const val MODEL_BYTES = 9_261_366L
        private const val MODEL_SHA256 = "E6783F59DFA8D477FAFF098AD87E104C0E8721C155A8EB9EB3C239788AD3C8AD"
        private val CLASS_NAMES = mapOf(1 to "person", 2 to "bicycle", 3 to "motorcycle", 4 to "car", 5 to "chair")

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { source ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02X".format(it.toInt() and 0xff) }
        }
    }
}

data class Detection(val classId: Int, val label: String, val score: Float,
    val left: Float, val top: Float, val right: Float, val bottom: Float)
