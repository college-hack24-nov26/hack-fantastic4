package com.vision.app.domain.voice

import com.vision.app.detector.HorizontalPosition
import com.vision.app.detector.SceneObservations
import com.vision.app.detector.SceneSnapshot
import com.vision.app.settings.UiLanguage
import java.util.Locale

/** Deterministic descriptions only; detector guesses never become route or safety advice. */
class VisualQuestionRouter {
    fun respond(question: String, language: UiLanguage, scene: SceneSnapshot, nowMs: Long): String? {
        val text = question.lowercase(Locale.ROOT).trim()
        val hinglish = language == UiLanguage.Hinglish
        if (contains(text, "safe", "safely", "cross", "walk", "route", "navigate", "obstacle", "hazard",
                "raasta", "sadak", "chal", "paar", "सुरक्षित", "रास्ता", "सड़क", "पार")) {
            return if (hinglish) "Main safe raasta ya crossing verify nahi kar sakta. Apne regular mobility aids aur zaroori madad par rely karein."
            else "I cannot verify a safe route or crossing. Please rely on established mobility aids and appropriate assistance."
        }
        if (contains(text, "color", "colour", "read the", "read this", "what does it say", "text on", "distance",
                "how far", "how many", "count the", "moving", "traffic light", "license plate", "number plate",
                "rang kya", "kitni door", "kitne", "padh", "रंग", "कितनी दूर")) {
            return if (hinglish) "Main camera se color, text, distance ya movement verify nahi kar sakta. Object guesses ko safety ke liye use na karein."
            else "I cannot verify color, text, distance, or movement from this camera. Do not use object guesses for safety."
        }
        if (!contains(text, "what do you see", "what can you see", "what's in front", "what is in front",
                "describe the scene", "describe what", "what objects", "camera detect", "can you see",
                "camera mein", "kya dikh", "kya dekh", "samne kya", "saamne kya", "कैमरा", "क्या दिख",
                "what's around", "what is around", "what is visible", "is there a", "do you see",
                "where is the", "in the frame", "in the camera", "in front of me", "around me")) return null
        if (!scene.cameraActive) return if (hinglish) "Camera monitoring band hai. Description ke liye camera start karein."
            else "Camera monitoring is off. Start the camera to request a description."
        val last = scene.lastProcessedAtMs
        if (last == null || nowMs - last !in 0..SceneObservations.FRESHNESS_MS) return if (hinglish)
            "Abhi recent camera observation nahi hai. Dobara try karein."
        else "I don't have a recent camera observation. Please try again."
        val candidates = scene.observations.filter { it.current && it.stable &&
            it.score >= SceneObservations.MIN_SCORE && nowMs - it.capturedAtMs in 0..SceneObservations.FRESHNESS_MS }
            .take(2)
        if (candidates.isEmpty()) return if (hinglish) "Current camera view mein main objects confidently identify nahi kar pa raha."
            else "I can't confidently identify objects in the current camera view."
        val items = candidates.map { observation ->
            val name = if (hinglish) when (observation.label) {
                "person" -> "insaan"; "bicycle" -> "cycle"; "chair" -> "kursi"; else -> observation.label
            } else observation.label
            val position = when (observation.position) {
                HorizontalPosition.Left -> if (hinglish) "left side" else "left side"
                HorizontalPosition.Center -> if (hinglish) "center" else "center"
                HorizontalPosition.Right -> if (hinglish) "right side" else "right side"
            }
            if (hinglish) "$position par $name" else "$name on the $position"
        }
        return if (hinglish) "Camera view mein shayad ${items.joinToString(" aur ")} hai."
        else "Possible ${items.joinToString(" and ")} in the camera view."
    }

    private fun contains(text: String, vararg phrases: String) = phrases.any { text.contains(it) }
}
