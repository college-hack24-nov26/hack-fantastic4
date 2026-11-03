package com.vision.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vision.app.ui.theme.VisionColors
import com.vision.app.ui.theme.VisionSpacing

@Composable
fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionColors.Background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VisionSpacing.Large, vertical = VisionSpacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(VisionSpacing.Large),
        content = content,
    )
}

@Composable
fun VisionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VisionColors.Cream,
            contentColor = VisionColors.CharcoalText,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VisionTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, color = VisionColors.Cream, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VisionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = VisionColors.Surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(VisionSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(VisionSpacing.Small),
            content = content,
        )
    }
}

@Composable
fun WaveMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 180.dp, height = 80.dp).clearAndSetSemantics { }) {
        val p = Path().apply {
            moveTo(size.width * 0.05f, size.height * 0.52f)
            cubicTo(size.width * 0.18f, size.height * 0.53f, size.width * 0.2f, size.height * 0.2f, size.width * 0.3f, size.height * 0.52f)
            cubicTo(size.width * 0.4f, size.height * 0.9f, size.width * 0.43f, size.height * 0.95f, size.width * 0.52f, size.height * 0.18f)
            cubicTo(size.width * 0.6f, size.height * -0.2f, size.width * 0.63f, size.height * 0.85f, size.width * 0.7f, size.height * 0.55f)
            cubicTo(size.width * 0.78f, size.height * 0.25f, size.width * 0.82f, size.height * 0.6f, size.width * 0.95f, size.height * 0.52f)
        }
        drawPath(p, VisionColors.Cream, style = Stroke(width = 5.dp.toPx()))
    }
}

@Composable
fun StepDots(current: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (index == current) 28.dp else 18.dp)
                    .height(5.dp)
                    .background(
                        if (index == current) VisionColors.Cream else VisionColors.Outline,
                        androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
fun CenteredIntro(title: String, body: String) {
    Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Text(body, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = VisionColors.MutedCream, textAlign = TextAlign.Center)
}
