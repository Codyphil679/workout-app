package com.codyphil.liftlog.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric

data class ChartPoint(val x: Long, val y: Double)

/** Line chart with a gradient fill, a highlighted latest point and min/max labels. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatY: (Double) -> String,
    formatX: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val pad = if (maxY == minY) (if (maxY == 0.0) 1.0 else maxY * 0.1) else (maxY - minY) * 0.12
    val lo = (minY - pad).coerceAtLeast(0.0)
    val hi = maxY + pad
    val minX = points.first().x
    val maxX = points.last().x

    Column(modifier) {
        Row {
            Text(formatY(maxY), style = MaterialTheme.typography.labelMedium.merge(Numeric), color = Brand.TextMid)
            Spacer(Modifier.weight(1f))
            Text("max", style = MaterialTheme.typography.labelSmall, color = Brand.TextLow)
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp)) {
            val w = size.width
            val h = size.height
            fun px(p: ChartPoint): Offset {
                val fx = if (maxX == minX) 0.5f else ((p.x - minX).toDouble() / (maxX - minX)).toFloat()
                val fy = ((p.y - lo) / (hi - lo)).toFloat()
                return Offset(fx * w, h - fy * h)
            }
            // grid
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(Brand.Line, Offset(0f, y), Offset(w, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            val offsets = points.map(::px)
            val line = Path().apply {
                offsets.forEachIndexed { i, o ->
                    if (i == 0) moveTo(o.x, o.y)
                    else {
                        val prev = offsets[i - 1]
                        val midX = (prev.x + o.x) / 2
                        cubicTo(midX, prev.y, midX, o.y, o.x, o.y)
                    }
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, h)
                lineTo(offsets.first().x, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Brand.Volt.copy(alpha = 0.28f), Brand.Volt.copy(alpha = 0f))))
            drawPath(line, Brand.voltGradient, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            offsets.forEach { drawCircle(Brand.Ink, 4.dp.toPx(), it); drawCircle(Brand.Volt, 2.5.dp.toPx(), it) }
            val last = offsets.last()
            drawCircle(Brand.Volt.copy(alpha = 0.25f), 11.dp.toPx(), last)
            drawCircle(Brand.Volt, 6.dp.toPx(), last)
        }
        Row {
            Text(formatX(minX), style = MaterialTheme.typography.labelMedium, color = Brand.TextLow)
            Spacer(Modifier.weight(1f))
            Text(formatY(minY) + " min", style = MaterialTheme.typography.labelMedium.merge(Numeric), color = Brand.TextLow)
            Spacer(Modifier.weight(1f))
            Text(formatX(maxX), style = MaterialTheme.typography.labelMedium, color = Brand.TextLow)
        }
    }
}
