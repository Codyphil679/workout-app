package com.codyphil.liftlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codyphil.liftlog.domain.Plates
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric

/** Plate colours by size rank (heaviest first). */
private val plateColors = listOf(Brand.Volt, Brand.Ice, Brand.Ember, Color(0xFFB794F6), Color(0xFFF2F4F7), Brand.TextMid, Brand.TextLow)

@Composable
fun PlateCalculatorDialog(
    initialDisplayWeight: Double?,
    bar: Double,
    unit: WeightUnit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialDisplayWeight?.takeIf { it > 0 }?.let { Units.formatNumber(it) } ?: "") }
    val total = Units.parse(text)
    val result = total?.let { Plates.forWeight(it, bar, unit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(text, { text = it }, label = "Total weight", suffix = unit.label, modifier = Modifier.fillMaxWidth())
                Text(
                    "Bar: ${Units.formatNumber(bar)} ${unit.label} (change in Settings)",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                )
                when {
                    result == null -> Text("Enter a weight.", color = Brand.TextMid)
                    result.belowBar -> Text("That's less than the empty bar.", color = Brand.Ember)
                    else -> {
                        BarbellDrawing(result.perSide, unit)
                        Eyebrow("Each side")
                        Text(
                            if (result.perSide.isEmpty()) "Just the bar"
                            else result.perSide.groupBy { it }.entries.joinToString("  +  ") { (p, list) ->
                                (if (list.size > 1) "${list.size}×" else "") + Units.formatNumber(p)
                            },
                            style = MaterialTheme.typography.titleLarge.merge(Numeric),
                        )
                        if (result.leftover > 0) {
                            Text(
                                "Can't make the last ${Units.formatNumber(result.leftover)} ${unit.label} with standard plates.",
                                style = MaterialTheme.typography.bodySmall, color = Brand.Ember,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** One side of a barbell: sleeve on the left, plates stacked outward, sized by weight. */
@Composable
private fun BarbellDrawing(perSide: List<Double>, unit: WeightUnit) {
    val all = Plates.available(unit)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val midY = size.height / 2
        drawRoundRect(Brand.PanelHigher, Offset(0f, midY - 6.dp.toPx()), Size(size.width, 12.dp.toPx()), CornerRadius(4f))
        drawRoundRect(Brand.TextLow, Offset(0f, midY - 16.dp.toPx()), Size(10.dp.toPx(), 32.dp.toPx()), CornerRadius(4f))
        var x = 14.dp.toPx()
        perSide.forEach { p ->
            val rank = all.indexOf(p).coerceAtLeast(0)
            val h = size.height * (1f - rank * 0.11f).coerceAtLeast(0.32f)
            val w = (16 - rank * 1.5f).coerceAtLeast(7f).dp.toPx()
            drawRoundRect(
                plateColors[rank.coerceAtMost(plateColors.lastIndex)],
                Offset(x, midY - h / 2), Size(w, h), CornerRadius(3.dp.toPx()),
            )
            x += w + 2.dp.toPx()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpeDialog(current: Double?, onDismiss: () -> Unit, onPick: (Double?) -> Unit) {
    val values = (12..20).map { it / 2.0 } // 6.0 .. 10.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How hard was that set?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    values.forEach { v ->
                        Pill(
                            "RPE " + Units.formatNumber(v),
                            color = if (v == current) Brand.Volt else Brand.TextMid,
                            modifier = Modifier.clickable { onPick(v) },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "RPE 10 = nothing left. 9 = one more rep possible. 8 = two more. 7 = three more.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = { if (current != null) TextButton(onClick = { onPick(null) }) { Text("Clear", color = Brand.Danger) } },
    )
}
