package com.codyphil.liftlog.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.timer.RestState
import com.codyphil.liftlog.timer.RestTimer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.delay

@Composable
fun RestTimerPanel(state: RestState, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) { now = System.currentTimeMillis(); delay(100) }
    }
    val remaining = state.remainingMillis(now)
    val progress = (remaining / (state.totalSeconds * 1000f)).coerceIn(0f, 1f)
    val urgent = remaining <= 10_000

    Panel(modifier, brush = Brand.heroGradient, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                ProgressRing(progress, if (urgent) Brand.emberGradient else Brand.voltGradient)
                Text(
                    Format.duration((remaining + 999) / 1000),
                    style = MaterialTheme.typography.titleLarge.merge(Numeric),
                    color = if (urgent) Brand.Ember else Brand.TextHi,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Rest", color = if (urgent) Brand.Ember else Brand.Volt)
                Text(state.label, style = MaterialTheme.typography.bodyMedium, color = Brand.TextMid, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimerButton("−15") { RestTimer.adjust(-15) }
                    TimerButton("+15") { RestTimer.adjust(15) }
                    TextButton(onClick = { RestTimer.skip() }) { Text("Skip", color = Brand.TextHi) }
                }
            }
        }
    }
}

@Composable
private fun TimerButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Brand.PanelHigher, contentColor = Brand.TextHi),
    ) { Text(text, style = MaterialTheme.typography.labelLarge.merge(Numeric)) }
}

@Composable
fun ProgressRing(progress: Float, brush: Brush, modifier: Modifier = Modifier.fillMaxSize(), stroke: Float = 9f) {
    Canvas(modifier) {
        val strokePx = stroke.dp.toPx()
        val inset = strokePx / 2
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        drawArc(
            color = Brand.PanelHigher, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokePx),
        )
        rotate(-90f) {
            drawArc(
                brush = brush, startAngle = 0f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

