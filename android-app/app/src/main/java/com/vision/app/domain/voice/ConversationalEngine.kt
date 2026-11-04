package com.vision.app.domain.voice

import com.vision.app.settings.UiLanguage
import java.io.File

/** Optional local text generator. Commands and safety decisions stay outside this interface. */
interface ConversationalEngine : AutoCloseable {
    val isReady: Boolean
    suspend fun load(model: File)
    suspend fun reply(text: String, language: UiLanguage): String
}
