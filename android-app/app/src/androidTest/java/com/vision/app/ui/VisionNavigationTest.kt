package com.vision.app.ui

import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vision.app.MainActivity
import com.vision.app.settings.UiLanguage
import com.vision.app.settings.VisionPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = VisionPreferencesRepository.from(compose.activity)

    @Test fun allSixScreensLanguageSwitchAndRestart() {
        runBlocking { preferences.setLanguage(UiLanguage.English) }
        visible("Get started")
        compose.onNodeWithText("Get started").assertHeightIsAtLeast(48.dp).performClick()
        visible("Carry Vision comfortably")
        click("Next")
        visible("Hear useful context")
        click("Next")
        visible("Stay in control")
        click("Continue")
        visible("Permissions")
        click("Continue")
        visible("Set up your voice")
        compose.onNodeWithText("Test English voice").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Test Hinglish voice").assertHeightIsAtLeast(48.dp)
        click("Hinglish")
        visible("Voice set karein")
        click("English")
        visible("Set up your voice")
        click("Hinglish")
        waitFor { runBlocking { preferences.preferences.first().language == UiLanguage.Hinglish } }
        click("Aage badhein")
        visible("Shuru karne ke liye tap karein")
        click("Voice assistant shuru karein")
        visible("SIMULATED CONTENT")
        visible("Yeh demo nahi bata sakta ki obstacle ya raasta safe hai.")
        compose.activityRule.scenario.recreate()
        visible("Yeh demo nahi bata sakta ki obstacle ya raasta safe hai.")
        assertEquals(UiLanguage.Hinglish, runBlocking { preferences.preferences.first().language })
        runBlocking { preferences.setLanguage(UiLanguage.English) }
    }

    @Test fun speechRatePersistsAndOnlyFeaturePermissionsDeclared() {
        runBlocking { preferences.setLanguage(UiLanguage.English); preferences.setSpeechRate(1f) }
        visible("Get started")
        click("Get started")
        click("Skip")
        visible("Permissions")
        click("Continue")
        visible("Set up your voice")
        compose.onNodeWithContentDescription("Speech speed").performSemanticsAction(SemanticsActions.SetProgress) { it(1.2f) }
        waitFor { runBlocking { preferences.preferences.first().speechRate >= 1.19f } }
        compose.activityRule.scenario.recreate()
        visible("Set up your voice")
        assertEquals(1.2f, runBlocking { preferences.preferences.first().speechRate }, 0.02f)
        val context = compose.activity
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val allowed = setOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
        for (permission in info.requestedPermissions.orEmpty()) {
            val protection = context.packageManager.getPermissionInfo(permission, 0).protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
            assertTrue("Unexpected dangerous permission: $permission", protection != android.content.pm.PermissionInfo.PROTECTION_DANGEROUS || permission in allowed)
        }
        runBlocking { preferences.setSpeechRate(1f) }
    }

    private fun visible(text: String) {
        waitFor { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertExists()
    }

    private fun click(text: String) {
        visible(text)
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 15_000, condition = condition)
    }
}
