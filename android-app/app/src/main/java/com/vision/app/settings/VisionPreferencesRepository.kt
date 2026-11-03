package com.vision.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.visionDataStore by preferencesDataStore(name = "vision_settings")

class VisionPreferencesRepository(private val store: DataStore<Preferences>) {
    private val languageKey = stringPreferencesKey("ui_language")
    private val speechRateKey = floatPreferencesKey("speech_rate")

    val preferences: Flow<VisionPreferences> = store.data.map { values ->
        VisionPreferences(
            language = UiLanguage.fromStored(values[languageKey]),
            speechRate = (values[speechRateKey] ?: 1f).coerceIn(0.7f, 1.3f),
        )
    }

    suspend fun setLanguage(language: UiLanguage) {
        store.edit { it[languageKey] = language.storedValue }
    }

    suspend fun setSpeechRate(rate: Float) {
        store.edit { it[speechRateKey] = rate.coerceIn(0.7f, 1.3f) }
    }

    companion object {
        fun from(context: Context) = VisionPreferencesRepository(context.applicationContext.visionDataStore)
    }
}
