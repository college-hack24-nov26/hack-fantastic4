package com.vision.app.domain.voice

import java.util.Locale

enum class CameraVoiceCommand { Start, Stop, EnableAnnouncements, DisableAnnouncements, Status }

/** The recognizer remains user-initiated and one-shot; these commands never reach Qwen. */
object CameraVoiceCommands {
    fun parse(utterance: String): CameraVoiceCommand? {
        val text = utterance.lowercase(Locale.ROOT).trim()
        val camera = listOf("camera", "कैमरा").any { text.contains(it) }
        val announcements = listOf("announcement", "announce", "object alerts", "objects bolo", "cheezein bolo", "चीज़ें बोलो").any { text.contains(it) }
        val enable = listOf("enable", "turn on", "switch on", "start", "chalu", "shuru", "चालू", "शुरू").any { text.contains(it) }
        val disable = listOf("disable", "turn off", "switch off", "stop", "band", "बंद").any { text.contains(it) }
        return when {
            announcements && disable -> CameraVoiceCommand.DisableAnnouncements
            announcements && enable -> CameraVoiceCommand.EnableAnnouncements
            camera && disable -> CameraVoiceCommand.Stop
            camera && enable -> CameraVoiceCommand.Start
            (camera || text.contains("listening") || text.contains("sun rahe")) &&
                listOf("status", "state", "running", "on hai", "chal raha", "active", "स्टेटस").any { text.contains(it) } -> CameraVoiceCommand.Status
            else -> null
        }
    }
}

enum class CameraRuntimeStatus { Off, Starting, Active, Failed }

/** Implemented by the app-level state holder, never by a second camera pipeline. */
interface CameraCommandPort {
    val cameraStatus: CameraRuntimeStatus
    fun startCamera(onComplete: (Boolean) -> Unit)
    fun stopCamera(onComplete: (Boolean) -> Unit)
    fun setAnnouncementsByVoice(enabled: Boolean, onComplete: (Boolean) -> Unit)
}
