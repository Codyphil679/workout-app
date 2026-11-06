package com.codyphil.liftlog.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.SessionSummary
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Recaps
import com.codyphil.liftlog.ui.recap.defaultRecapMonth
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.EmptyState
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.StatTile
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onOpenSession: (Long) -> Unit, onOpenRecap: () -> Unit = {}) {
    val container = appContainer()
    val sessions by container.repository.observeFinishedSessions().collectAsStateWithLifecycle(null)
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val list = sessions ?: return

    if (list.isEmpty()) {
        EmptyState(
            Icons.Rounded.EventNote, "No workouts yet",
            "Finish a workout and it shows up here with every set, weight and rest time.",
            Modifier.statusBarsPadding(),
        )
        return
    }
    val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val grouped = list.groupBy { monthFmt.format(Date(it.startedAt)) }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Eyebrow("Log", color = Brand.Volt)
            Text("History", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Workouts", list.size.toString(), Modifier.weight(1f), accent = Brand.Volt)
                StatTile("Sets", "%,d".format(list.sumOf { it.setCount }), Modifier.weight(1f))
                StatTile(
                    "Lifted", compact(Units.fromKg(list.sumOf { it.volumeKg }, settings.unit)), Modifier.weight(1f),
                    unit = settings.unit.label,
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Panel(onClick = onOpenRecap, brush = Brand.heroGradient, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Monthly recap", color = Brand.Volt)
                        Text(Recaps.monthName(defaultRecapMonth()), style = MaterialTheme.typography.titleLarge)
                        Text("Records, streaks and totals. Shareable as an image.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.Volt)
                }
            }
        }
        grouped.forEach { (month, items) ->
            item(key = month) { Eyebrow(month, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) }
            items(items, key = { it.id }) { SessionCard(it, settings.unit) { onOpenSession(it.id) } }
        }
    }
}

@Composable
private fun SessionCard(s: SessionSummary, unit: WeightUnit, onClick: () -> Unit) {
    Panel(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brand.PanelHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SimpleDateFormat("EEE", Locale.getDefault()).format(Date(s.startedAt)).uppercase(),
                        style = MaterialTheme.typography.labelSmall, color = Brand.Volt)
                    Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(s.startedAt)),
                        style = MaterialTheme.typography.titleLarge.merge(Numeric))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        s.endedAt?.let { Format.durationShort(it - s.startedAt) },
                        "${s.setCount} sets",
                        Units.formatVolume(s.volumeKg, unit),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Brand.TextMid,
                )
            }
        }
    }
}

internal fun compact(v: Double): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
    v >= 10_000 -> "%.0fk".format(v / 1_000)
    v >= 1_000 -> "%.1fk".format(v / 1_000)
    else -> "%.0f".format(v)
}
