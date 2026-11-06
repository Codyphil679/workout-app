package com.codyphil.liftlog.ui.recovery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.domain.RecoveryStatus
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.theme.Brand

fun RecoveryStatus.color(): Color = when (this) {
    RecoveryStatus.RECOVERING -> Brand.Ember
    RecoveryStatus.READY -> Brand.Volt
    RecoveryStatus.FRESH -> Brand.Ice
}

/** A region of the figure in fractions of the canvas: x, y, width, height, and which muscle colours it. */
private data class Region(val x: Float, val y: Float, val w: Float, val h: Float, val muscle: Muscle?, val round: Float = 0.45f)

private val skeleton = listOf(
    Region(0.42f, 0.02f, 0.16f, 0.09f, null, 1f),            // head
    Region(0.46f, 0.11f, 0.08f, 0.03f, null, 0.3f),          // neck
    Region(0.32f, 0.36f, 0.36f, 0.12f, null, 0.3f),          // hips
    Region(0.13f, 0.38f, 0.08f, 0.15f, null),                // forearm L
    Region(0.79f, 0.38f, 0.08f, 0.15f, null),                // forearm R
)

private val front = listOf(
    Region(0.20f, 0.14f, 0.14f, 0.08f, Muscle.SHOULDERS), Region(0.66f, 0.14f, 0.14f, 0.08f, Muscle.SHOULDERS),
    Region(0.34f, 0.15f, 0.155f, 0.10f, Muscle.CHEST, 0.3f), Region(0.505f, 0.15f, 0.155f, 0.10f, Muscle.CHEST, 0.3f),
    Region(0.16f, 0.22f, 0.09f, 0.15f, Muscle.BICEPS), Region(0.75f, 0.22f, 0.09f, 0.15f, Muscle.BICEPS),
    Region(0.38f, 0.255f, 0.24f, 0.11f, Muscle.CORE, 0.25f),
    Region(0.32f, 0.47f, 0.165f, 0.23f, Muscle.QUADS), Region(0.515f, 0.47f, 0.165f, 0.23f, Muscle.QUADS),
    Region(0.34f, 0.72f, 0.12f, 0.20f, Muscle.CALVES), Region(0.54f, 0.72f, 0.12f, 0.20f, Muscle.CALVES),
)

private val back = listOf(
    Region(0.20f, 0.14f, 0.14f, 0.08f, Muscle.SHOULDERS), Region(0.66f, 0.14f, 0.14f, 0.08f, Muscle.SHOULDERS),
    Region(0.34f, 0.15f, 0.32f, 0.21f, Muscle.BACK, 0.25f),
    Region(0.16f, 0.22f, 0.09f, 0.15f, Muscle.TRICEPS), Region(0.75f, 0.22f, 0.09f, 0.15f, Muscle.TRICEPS),
    Region(0.33f, 0.37f, 0.165f, 0.10f, Muscle.GLUTES), Region(0.505f, 0.37f, 0.165f, 0.10f, Muscle.GLUTES),
    Region(0.32f, 0.48f, 0.165f, 0.22f, Muscle.HAMSTRINGS), Region(0.515f, 0.48f, 0.165f, 0.22f, Muscle.HAMSTRINGS),
    Region(0.34f, 0.72f, 0.12f, 0.20f, Muscle.CALVES), Region(0.54f, 0.72f, 0.12f, 0.20f, Muscle.CALVES),
)

/**
 * Front and back figures. Pass [figureHeight] for a compact fixed-size version (e.g. inside a card);
 * otherwise the figures fill the available width.
 */
@Composable
fun BodyMap(status: Map<Muscle, RecoveryStatus>, modifier: Modifier = Modifier, figureHeight: Dp? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        listOf("Front" to front, "Back" to back).forEach { (label, regions) ->
            Column(
                if (figureHeight == null) Modifier.weight(1f) else Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val canvasModifier = if (figureHeight == null) Modifier.fillMaxWidth().aspectRatio(0.5f)
                else Modifier.height(figureHeight).width(figureHeight / 2)
                Canvas(canvasModifier) {
                    skeleton.forEach { drawRegion(it, Brand.PanelHigher) }
                    regions.forEach { r ->
                        val c = r.muscle?.let { status[it] }?.color() ?: Brand.PanelHigher
                        drawRegion(r, c.copy(alpha = 0.9f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Eyebrow(label)
            }
        }
    }
}

private fun DrawScope.drawRegion(r: Region, color: Color) {
    val w = r.w * size.width
    val h = r.h * size.height
    drawRoundRect(
        color, Offset(r.x * size.width, r.y * size.height), Size(w, h),
        CornerRadius(minOf(w, h) * r.round, minOf(w, h) * r.round),
    )
}

@Composable
fun RecoveryLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RecoveryStatus.entries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.height(10.dp).aspectRatio(1f)) { drawCircle(s.color()) }
                Text("  " + s.label, style = MaterialTheme.typography.labelMedium, color = Brand.TextMid)
            }
        }
    }
}
