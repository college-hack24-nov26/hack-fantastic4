package com.vision.app.voice

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.vision.app.domain.voice.ConversationalEngine
import com.vision.app.settings.UiLanguage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** LiteRT-LM v0.18.0 CPU path; no model is bundled or fetched by the application. */
class LiteRtConversationEngine : ConversationalEngine {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    override val isReady get() = conversation != null

    override suspend fun load(model: File) = withContext(Dispatchers.Default) {
        close()
        require(model.isFile && model.length() > 0) { "Model file is missing" }
        val next = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU()))
        try {
            next.initialize()
            val chat = next.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are Vision, a concise offline voice companion. Reply in one or two short sentences. " +
                            "Use conversational English or simple Hinglish as requested. " +
                            "You have no visual input. Never describe surroundings, routes, hazards, or safety conditions. " +
                            "Do not claim to have performed actions or accessed the internet."
                    ),
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                    maxOutputToken = 96,
                )
            )
            engine = next
            conversation = chat
        } catch (error: Exception) {
            next.close()
            throw error
        }
    }

    override suspend fun reply(text: String, language: UiLanguage): String = withContext(Dispatchers.Default) {
        val chat = requireNotNull(conversation) { "Conversation model is not loaded" }
        val instruction = if (language == UiLanguage.Hinglish) "Reply in simple conversational Hinglish." else "Reply in English."
        val answer = chat.sendMessage("$instruction\nUser: $text").contents.contents
            .filterIsInstance<Content.Text>().joinToString("") { it.text }.trim().take(500)
        val unsafeClaims = listOf("i can see", "i see an", "i see a ", "in front of you", "your path is clear", "path is safe", "safe to cross", "turn left", "turn right", "aage safe", "raasta safe")
        if (unsafeClaims.any { answer.contains(it, ignoreCase = true) }) {
            if (language == UiLanguage.Hinglish) "Main surroundings ya raaste ki safety verify nahi kar sakta." else "I cannot verify your surroundings or route safety."
        } else answer
    }

    override fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
