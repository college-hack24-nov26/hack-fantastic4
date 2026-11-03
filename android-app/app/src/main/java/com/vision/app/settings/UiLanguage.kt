package com.vision.app.settings

enum class UiLanguage(val storedValue: String) {
    English("english"),
    Hinglish("hinglish");

    companion object {
        fun fromStored(value: String?): UiLanguage = entries.firstOrNull { it.storedValue == value } ?: English
    }
}

data class VisionPreferences(
    val language: UiLanguage = UiLanguage.English,
    val speechRate: Float = 1f,
)
