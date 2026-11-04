package com.vision.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPreferencesReadbackTest {
    @Test fun languageSurvivesReopen() = verifyReopen(
        write = { it.setLanguage(UiLanguage.Hinglish) },
        check = { assertEquals(UiLanguage.Hinglish, it.preferences.first().language) },
    )

    @Test fun rateSurvivesReopen() = verifyReopen(
        write = { it.setSpeechRate(1.2f) },
        check = { assertEquals(1.2f, it.preferences.first().speechRate) },
    )

    @Test fun automaticAnnouncementsDefaultOffAndSurviveReopen() = verifyReopen(
        write = {
            assertFalse(it.preferences.first().automaticAnnouncements)
            it.setAutomaticAnnouncements(true)
        },
        check = { assertTrue(it.preferences.first().automaticAnnouncements) },
    )

    private fun verifyReopen(
        write: suspend (VisionPreferencesRepository) -> Unit,
        check: suspend (VisionPreferencesRepository) -> Unit,
    ) = runBlocking {
        val directory = Files.createTempDirectory("vision-preference-readback").toFile()
        val file = File(directory, "settings.preferences_pb")
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            write(VisionPreferencesRepository(PreferenceDataStoreFactory.create(scope = writeScope, produceFile = { file })))
        } finally {
            writeScope.cancel()
        }
        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            check(VisionPreferencesRepository(PreferenceDataStoreFactory.create(scope = readScope, produceFile = { file })))
        } finally {
            readScope.cancel()
            directory.deleteRecursively()
        }
    }
}
