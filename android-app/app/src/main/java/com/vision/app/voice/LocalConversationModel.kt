package com.vision.app.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports only the reviewed Qwen3-0.6B LiteRT artifact selected by the user. */
class LocalConversationModel(private val context: Context) {
    private val modelDir get() = File(context.filesDir, "models")
    val installedFile get() = File(modelDir, "Qwen3-0.6B.litertlm")

    suspend fun import(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: throw IllegalArgumentException("Could not read model name")
        require(name == "Qwen3-0.6B.litertlm") { "Choose Qwen3-0.6B.litertlm" }
        modelDir.mkdirs()
        val temporary = File(modelDir, "Qwen3-0.6B.importing")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open model file" }
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= 700_000_000L) { "Model file is too large" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(bytes > 500_000_000L) { "Model file is incomplete" }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(actual == EXPECTED_SHA256) { "Model checksum does not match the reviewed artifact" }
            Files.move(temporary.toPath(), installedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            installedFile
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val EXPECTED_SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    }
}
