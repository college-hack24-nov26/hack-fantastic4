package com.vision.app.ui

import com.vision.app.settings.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VisionStringsTest {
    @Test fun selectedLanguageChangesScreenAndSafetyCopy() {
        val english = VisionStrings.forLanguage(UiLanguage.English)
        val hinglish = VisionStrings.forLanguage(UiLanguage.Hinglish)
        assertNotEquals(english.getStarted, hinglish.getStarted)
        assertNotEquals(english.assistantCaution, hinglish.assistantCaution)
        assertNotEquals(english.sampleDescription, hinglish.sampleDescription)
        assertEquals(3, hinglish.onboarding.size)
        assertEquals("Step 2, total 3", hinglish.stepOf(2, 3))
    }

    @Test fun unknownSavedLanguageDefaultsToEnglish() {
        assertEquals(UiLanguage.English, UiLanguage.fromStored("unknown"))
    }
}
