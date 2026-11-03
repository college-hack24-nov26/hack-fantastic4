package com.vision.app.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vision.app.voice.AndroidVoiceOutput
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionTtsDeviceTest {
    @Test fun installedVoicesAndBothPreviewPaths() {
        val output = AndroidVoiceOutput(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val deadline = System.currentTimeMillis() + 15_000
            while (!output.ready.value && System.currentTimeMillis() < deadline) Thread.sleep(150)
            assertTrue("TTS engine initialization", output.ready.value)
            val inventory = output.inventory.value
            assertNotNull("TTS engine should be identified", inventory.engine)
            Log.i("VisionDeviceTest", "TTS engine=${inventory.engine}; reported offline voices=${inventory.voices}")
            assertTrue(inventory.voices.all { it.locale.startsWith("en") || it.locale.startsWith("hi") })
            val english = output.preview(hinglish = false, speechRate = 1.2f)
            val hinglish = output.preview(hinglish = true, speechRate = 0.8f)
            Log.i("VisionDeviceTest", "English preview=$english; Hinglish preview=$hinglish")
            if (english.queued) assertTrue(inventory.voices.any { it.name == english.selectedVoice?.name })
            if (hinglish.queued) assertTrue(inventory.voices.any { it.name == hinglish.selectedVoice?.name })
        } finally {
            output.close()
        }
    }
}
