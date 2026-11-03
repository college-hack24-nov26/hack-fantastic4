package com.vision.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vision.app.ui.theme.VisionColors
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionColorContrastTest {
    @Test fun ivoryAndSupportingTextRemainReadableOnCharcoal() {
        assertTrue(contrast(VisionColors.Cream, VisionColors.Background) >= 7.0)
        assertTrue(contrast(VisionColors.MutedCream, VisionColors.Background) >= 7.0)
        assertTrue(contrast(VisionColors.Cream, VisionColors.Surface) >= 7.0)
        assertTrue(contrast(VisionColors.CharcoalText, VisionColors.Cream) >= 7.0)
    }

    private fun contrast(first: Color, second: Color): Double {
        val lighter = maxOf(first.luminance(), second.luminance()).toDouble()
        val darker = minOf(first.luminance(), second.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }
}
