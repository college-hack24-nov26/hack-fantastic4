package com.vision.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionPreferencesRepositoryTest {
    @Test fun languageAndRateSurviveStoreRecreation() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "vision-preferences-test").apply { mkdirs() }
        val file = File(directory, "settings.preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val firstStore = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
            val repository = VisionPreferencesRepository(firstStore)
            repository.setLanguage(UiLanguage.Hinglish)
            repository.setSpeechRate(1.2f)
            assertEquals(UiLanguage.Hinglish, repository.preferences.first().language)
        } finally {
            firstScope.cancel()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = VisionPreferencesRepository(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
            assertEquals(UiLanguage.Hinglish, reopened.preferences.first().language)
            assertEquals(1.2f, reopened.preferences.first().speechRate)
        } finally {
            secondScope.cancel()
            directory.deleteRecursively()
        }
    }
}
