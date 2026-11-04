package com.vision.app.domain.voice

/** One active TTS request. Lower-priority announcements are discarded, never queued. */
enum class SpeechPriority { UserResponse, AssistantResponse, AutomaticObservation }

class SpeechPriorityCoordinator {
    private var nextId = 0L
    private var active: Pair<Long, SpeechPriority>? = null

    fun begin(priority: SpeechPriority): Long? {
        val current = active
        if (current != null && (priority.ordinal > current.second.ordinal || priority == SpeechPriority.AutomaticObservation)) return null
        val id = ++nextId
        active = id to priority
        return id
    }

    fun isCurrent(id: Long): Boolean = active?.first == id

    fun complete(id: Long) { if (isCurrent(id)) active = null }

    fun cancel() { active = null; nextId++ }
}
