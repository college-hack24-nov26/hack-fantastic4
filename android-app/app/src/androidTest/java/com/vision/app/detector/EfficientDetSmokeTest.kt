package com.vision.app.detector

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the locally packaged model can initialize and infer on a real Android runtime. */
@RunWith(AndroidJUnit4::class)
class EfficientDetSmokeTest {
    @Test fun bundledModelRunsOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EfficientDetDetector(context).use { it.load() }
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(EfficientDetDetector.ASSET).build())
            .setRunningMode(RunningMode.IMAGE).build()
        val image = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        try {
            ObjectDetector.createFromOptions(context, options).use { detector ->
                assertNotNull(detector.detect(BitmapImageBuilder(image).build()))
            }
        } finally {
            image.recycle()
        }
    }
}
