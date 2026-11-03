package com.vision.app.domain.voice

import com.vision.app.settings.UiLanguage
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface DialogueAction {
    data object None : DialogueAction
    data object StopSpeaking : DialogueAction
    data class SetLanguage(val language: UiLanguage) : DialogueAction
    data class SetRate(val rate: Float) : DialogueAction
}

data class DialogueReply(val text: String, val action: DialogueAction = DialogueAction.None, val speak: Boolean = true)

/** Small deterministic command engine. It never generates visual observations. */
class DialogueEngine(private val clock: Clock = Clock.systemDefaultZone()) {
    private var lastReply: String? = null
    private var lastTopic: String? = null

    fun respond(utterance: String, language: UiLanguage, speechRate: Float): DialogueReply {
        val words = utterance.lowercase(Locale.ROOT).trim()
        val hinglish = language == UiLanguage.Hinglish
        val reply = when {
            has(words, "stop speaking", "stop talking", "bolna band", "chup", "रुको", "बंद करो") ->
                DialogueReply(if (hinglish) "Theek hai, main chup hoon." else "Okay, I stopped speaking.", DialogueAction.StopSpeaking, speak = false)
            has(words, "repeat", "say that again", "phir se", "dobara", "दोबारा") ->
                DialogueReply(lastReply ?: help(hinglish))
            has(words, "hinglish", "hindi", "हिंदी") && has(words, "language", "switch", "change", "bolo", "बोलो", "बदलो") ->
                DialogueReply("Theek hai, ab Hinglish mein baat karenge.", DialogueAction.SetLanguage(UiLanguage.Hinglish))
            has(words, "english", "अंग्रेजी") && has(words, "language", "switch", "change", "speak", "bolo", "बोलो", "बदलो") ->
                DialogueReply("Okay, I will speak English.", DialogueAction.SetLanguage(UiLanguage.English))
            has(words, "speed", "rate", "fast", "slow", "tez", "dheere", "तेज", "धीरे") -> {
                val next = when {
                    has(words, "slow", "slower", "dheere", "धीरे") -> (speechRate - 0.1f).coerceAtLeast(0.7f)
                    has(words, "fast", "faster", "tez", "तेज") -> (speechRate + 0.1f).coerceAtMost(1.3f)
                    else -> 1f
                }
                DialogueReply(if (hinglish) "Bolne ki speed badal di." else "Speech speed changed.", DialogueAction.SetRate(next))
            }
            has(words, "tomorrow", "kal", "कल") && (lastTopic == "date" || has(words, "date", "day", "tarikh", "तारीख", "दिन")) -> {
                val day = LocalDate.now(clock).plusDays(1)
                lastTopic = "date"
                DialogueReply(if (hinglish) "Kal ${day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("hi-IN")))} hai." else "Tomorrow is ${day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))}.")
            }
            has(words, "time", "kitne baje", "समय", "बजे") -> {
                val time = LocalTime.now(clock).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                lastTopic = "time"
                DialogueReply(if (hinglish) "Abhi $time baje hain." else "It is $time.")
            }
            has(words, "date", "today", "day", "tarikh", "aaj", "तारीख", "आज") -> {
                val day = LocalDate.now(clock)
                lastTopic = "date"
                DialogueReply(if (hinglish) "Aaj ${day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("hi-IN")))} hai." else "Today is ${day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))}.")
            }
            has(words, "help", "commands", "madad", "मदद") -> DialogueReply(help(hinglish))
            has(words, "see", "front", "around", "describe", "object", "obstacle", "hazard", "path", "safe", "raasta", "samne", "aage", "देख", "सामने", "रास्ता", "सुरक्षित") ->
                DialogueReply(if (hinglish) "Visual detection abhi available nahi hai. Main aas-paas ya raaste ki safety nahi bata sakta." else "Visual detection is not available yet. I cannot describe your surroundings or judge route safety.")
            else -> DialogueReply(if (hinglish) "Main abhi time, date, language aur speech speed mein help kar sakta hoon. Visual sawaalon ke liye model abhi ready nahi hai." else "I can help with time, date, language, and speech speed. Visual questions need the model, which is not ready yet.")
        }
        if (reply.speak) lastReply = reply.text
        return reply
    }

    private fun help(hinglish: Boolean) = if (hinglish)
        "Aap time ya date pooch sakte hain, phir se bolne ko keh sakte hain, speed badal sakte hain, ya language switch kar sakte hain."
    else
        "Ask for the time or date, say repeat, change speech speed or language, or say stop speaking."

    private fun has(text: String, vararg phrases: String) = phrases.any { text.contains(it) }

}
