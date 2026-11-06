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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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

// ---------------------------------------------------------------------------------------------
// Figure geometry. Everything is drawn in a 200 × 440 design space and scaled to the canvas.
// Shapes are described for the figure's left side (viewer's left) and mirrored around x = 100.
// ---------------------------------------------------------------------------------------------

private const val W = 200f
private const val H = 440f
private const val ASPECT = W / H

/** A tiny path recipe that can be built as-is or mirrored. */
private class Shape(private val ops: List<FloatArray>) {
    fun path(mirror: Boolean): Path = Path().apply {
        fun x(v: Float) = if (mirror) W - v else v
        ops.forEach { o ->
            when (o.size) {
                0 -> close()
                2 -> if (isEmpty) moveTo(x(o[0]), o[1]) else lineTo(x(o[0]), o[1])
                6 -> cubicTo(x(o[0]), o[1], x(o[2]), o[3], x(o[4]), o[5])
            }
        }
    }
}

private class ShapeBuilder {
    val ops = mutableListOf<FloatArray>()
    fun p(x: Float, y: Float) { ops += floatArrayOf(x, y) }
    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) { ops += floatArrayOf(x1, y1, x2, y2, x, y) }
    fun z() { ops += FloatArray(0) }
}

private fun shape(block: ShapeBuilder.() -> Unit) = Shape(ShapeBuilder().apply(block).ops)

/** Left half of the body outline, from the top of the neck to the crotch. Mirrored to close the silhouette. */
private val outlineHalf: List<FloatArray> = ShapeBuilder().apply {
    p(91f, 47f)
    c(91f, 54f, 89f, 59f, 86f, 62f)          // side of neck
    c(80f, 65f, 72f, 66f, 64f, 69f)          // trapezius slope
    c(54f, 72f, 47f, 82f, 47f, 96f)          // shoulder cap
    c(46f, 112f, 45f, 128f, 44f, 146f)       // outer upper arm
    c(42f, 160f, 39f, 176f, 37f, 194f)       // outer forearm
    c(36f, 202f, 35f, 208f, 34f, 214f)       // wrist
    c(31f, 222f, 32f, 232f, 37f, 236f)       // hand
    c(42f, 238f, 46f, 232f, 47f, 224f)
    c(48f, 216f, 48f, 210f, 48f, 205f)       // inner wrist
    c(51f, 186f, 56f, 168f, 58f, 150f)       // inner forearm
    c(60f, 136f, 63f, 118f, 66f, 104f)       // inner upper arm to armpit
    c(68f, 120f, 70f, 140f, 74f, 162f)       // torso side
    c(76f, 172f, 75f, 182f, 71f, 194f)       // waist to hip
    c(66f, 214f, 64f, 240f, 66f, 266f)       // outer thigh
    c(68f, 282f, 70f, 296f, 70f, 306f)       // knee
    c(66f, 324f, 66f, 344f, 71f, 364f)       // outer calf
    c(73f, 376f, 75f, 388f, 76f, 398f)       // ankle
    c(72f, 406f, 70f, 414f, 78f, 416f)       // foot
    c(84f, 417f, 90f, 416f, 92f, 412f)
    c(92f, 404f, 91f, 398f, 91f, 392f)       // inner ankle
    c(94f, 370f, 95f, 346f, 92f, 326f)       // inner calf
    c(91f, 316f, 92f, 306f, 93f, 298f)       // inner knee
    c(97f, 270f, 99f, 248f, 100f, 232f)      // inner thigh to crotch
}.ops

/** Each half closed along the centre line, for filling. */
private fun halfFill(mirror: Boolean) = Shape(outlineHalf).path(mirror).apply { lineTo(100f, 232f); lineTo(100f, 47f); close() }
/** The outer edge only, for the outline stroke. */
private fun halfEdge(mirror: Boolean) = Shape(outlineHalf).path(mirror)

private val head = shape {
    p(100f, 6f)
    c(112f, 6f, 120f, 16f, 119f, 30f)
    c(118f, 42f, 110f, 50f, 100f, 50f)
    c(90f, 50f, 82f, 42f, 81f, 30f)
    c(80f, 16f, 88f, 6f, 100f, 6f)
    z()
}

// ---- Front muscles (left side; mirrored) ----
private val frontDelt = shape {
    p(70f, 68f); c(60f, 68f, 50f, 78f, 50f, 94f); c(50f, 104f, 54f, 110f, 60f, 108f)
    c(64f, 98f, 68f, 86f, 77f, 76f); c(76f, 72f, 74f, 69f, 70f, 68f); z()
}
private val pec = shape {
    p(98f, 74f); c(90f, 72f, 82f, 72f, 77f, 77f); c(70f, 86f, 66f, 98f, 69f, 106f)
    c(76f, 114f, 90f, 114f, 98f, 108f); z()
}
private val biceps = shape {
    p(56f, 104f); c(50f, 112f, 48f, 128f, 49f, 143f); c(52f, 150f, 60f, 150f, 63f, 143f)
    c(66f, 130f, 66f, 116f, 64f, 106f); c(62f, 101f, 58f, 101f, 56f, 104f); z()
}
private val forearmFront = shape {
    p(52f, 154f); c(46f, 166f, 42f, 182f, 41f, 200f); c(44f, 204f, 46f, 202f, 47f, 198f)
    c(50f, 182f, 55f, 168f, 57f, 156f); c(56f, 152f, 54f, 151f, 52f, 154f); z()
}
private val oblique = shape {
    p(74f, 116f); c(70f, 134f, 72f, 154f, 78f, 172f); c(80f, 182f, 82f, 188f, 86f, 192f)
    p(87f, 120f); c(83f, 116f, 78f, 114f, 74f, 116f); z()
}
/** One ab segment (left column); three rows stacked. */
private fun abBlock(top: Float, h: Float) = shape {
    p(98f, top); c(93f, top, 89f, top + 1f, 88f, top + 4f); p(88f, top + h - 3f)
    c(89f, top + h, 93f, top + h, 98f, top + h); z()
}
private val abs = listOf(abBlock(114f, 20f), abBlock(137f, 20f), abBlock(160f, 20f))
private val lowerAbs = shape {
    p(98f, 183f); p(88f, 183f); c(88f, 196f, 92f, 210f, 98f, 220f); z()
}
private val quad = shape {
    p(76f, 204f); c(68f, 222f, 66f, 252f, 70f, 280f); c(73f, 292f, 78f, 300f, 85f, 302f)
    c(91f, 303f, 95f, 298f, 96f, 290f); c(98f, 262f, 97f, 232f, 92f, 210f); c(88f, 202f, 81f, 200f, 76f, 204f); z()
}
private val shinCalf = shape {
    p(71f, 320f); c(67f, 336f, 68f, 356f, 74f, 374f); c(76f, 382f, 80f, 386f, 84f, 384f)
    c(89f, 366f, 91f, 344f, 89f, 324f); c(85f, 314f, 76f, 312f, 71f, 320f); z()
}

// ---- Back muscles ----
private val traps = shape {   // centre piece, drawn unmirrored
    p(100f, 50f); c(94f, 56f, 86f, 61f, 76f, 66f); c(86f, 72f, 94f, 88f, 100f, 122f)
    c(106f, 88f, 114f, 72f, 124f, 66f); c(114f, 61f, 106f, 56f, 100f, 50f); z()
}
private val rearDelt = frontDelt
private val lat = shape {
    p(76f, 80f); c(70f, 96f, 70f, 120f, 76f, 142f); c(80f, 156f, 88f, 166f, 97f, 170f)
    p(98f, 128f); c(92f, 104f, 86f, 88f, 76f, 80f); z()
}
private val lowerBack = shape {
    p(99f, 174f); c(94f, 172f, 90f, 172f, 88f, 176f); c(88f, 182f, 90f, 188f, 99f, 190f); z()
}
private val triceps = shape {
    p(55f, 104f); c(49f, 114f, 47f, 130f, 48f, 146f); c(51f, 151f, 57f, 150f, 60f, 144f)
    c(64f, 132f, 66f, 118f, 64f, 107f); c(61f, 101f, 57f, 101f, 55f, 104f); z()
}
private val forearmBack = forearmFront
private val glute = shape {
    p(99f, 196f); c(90f, 192f, 76f, 196f, 72f, 210f); c(70f, 224f, 78f, 236f, 90f, 238f)
    c(95f, 238f, 99f, 234f, 99f, 228f); z()
}
private val hamstring = shape {
    p(74f, 242f); c(69f, 262f, 70f, 284f, 76f, 300f); c(80f, 304f, 88f, 304f, 93f, 300f)
    c(97f, 284f, 98f, 262f, 96f, 244f); c(90f, 240f, 80f, 238f, 74f, 242f); z()
}
private val calfBack = shape {
    p(72f, 314f); c(66f, 330f, 67f, 352f, 76f, 366f); c(81f, 372f, 88f, 370f, 91f, 360f)
    c(94f, 344f, 93f, 326f, 88f, 314f); c(84f, 308f, 76f, 308f, 72f, 314f); z()
}

/** (shape, muscle, mirrored-pair?) — null muscle = untracked, drawn in the neutral tone. */
private data class Part(val shape: Shape, val muscle: Muscle?, val pair: Boolean = true)

private val frontParts = listOf(
    Part(forearmFront, null), Part(frontDelt, Muscle.SHOULDERS), Part(pec, Muscle.CHEST), Part(biceps, Muscle.BICEPS),
    Part(oblique, Muscle.CORE), Part(lowerAbs, Muscle.CORE),
) + abs.map { Part(it, Muscle.CORE) } + listOf(
    Part(quad, Muscle.QUADS), Part(shinCalf, Muscle.CALVES),
)

private val backParts = listOf(
    Part(forearmBack, null), Part(traps, Muscle.BACK, pair = false), Part(rearDelt, Muscle.SHOULDERS),
    Part(lat, Muscle.BACK), Part(lowerBack, Muscle.BACK), Part(triceps, Muscle.TRICEPS),
    Part(glute, Muscle.GLUTES), Part(hamstring, Muscle.HAMSTRINGS), Part(calfBack, Muscle.CALVES),
)

private val BodyBase = Color(0xFF1B1F26)
private val BodyEdge = Color(0xFF39404B)
private val Untracked = Color(0xFF2A303A)

@Composable
private fun Figure(parts: List<Part>, status: Map<Muscle, RecoveryStatus>, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / W, size.height / H, pivot = Offset.Zero) {
            drawPath(halfFill(false), BodyBase)
            drawPath(halfFill(true), BodyBase)
            drawPath(head.path(false), BodyBase)
            parts.forEach { part ->
                val base = part.muscle?.let { status[it] }?.color() ?: Untracked
                val paths = if (part.pair) listOf(part.shape.path(false), part.shape.path(true)) else listOf(part.shape.path(false))
                paths.forEach { drawMuscle(it, base, part.muscle != null) }
            }
            // Outline last so edges stay crisp over the muscles.
            drawPath(halfEdge(false), BodyEdge, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawPath(halfEdge(true), BodyEdge, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawPath(head.path(false), BodyEdge, style = Stroke(1.6f))
        }
    }
}

/** A muscle with a top-lit gradient for depth and a dark seam separating it from its neighbours. */
private fun DrawScope.drawMuscle(path: Path, color: Color, tracked: Boolean) {
    val bounds = path.getBounds()
    val brush = if (tracked) Brush.verticalGradient(
        listOf(lerp(color, Color.White, 0.18f), color, lerp(color, Color.Black, 0.28f)),
        startY = bounds.top, endY = bounds.bottom,
    ) else Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.2f)), startY = bounds.top, endY = bounds.bottom)
    drawPath(path, brush)
    drawPath(path, Brand.Ink.copy(alpha = 0.85f), style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha,
)

/**
 * Front and back figures. Pass [figureHeight] for a compact fixed-size version (e.g. inside a card);
 * otherwise the figures fill the available width.
 */
@Composable
fun BodyMap(status: Map<Muscle, RecoveryStatus>, modifier: Modifier = Modifier, figureHeight: Dp? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        listOf("Front" to frontParts, "Back" to backParts).forEach { (label, parts) ->
            Column(
                if (figureHeight == null) Modifier.weight(1f) else Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val m = if (figureHeight == null) Modifier.fillMaxWidth(0.8f).aspectRatio(ASPECT)
                else Modifier.height(figureHeight).width(figureHeight * ASPECT)
                Figure(parts, status, m)
                Spacer(Modifier.height(4.dp))
                Eyebrow(label)
            }
        }
    }
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
