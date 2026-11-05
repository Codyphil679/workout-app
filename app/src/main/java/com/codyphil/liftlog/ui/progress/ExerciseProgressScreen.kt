package com.codyphil.liftlog.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.domain.ExerciseAnalytics
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.SessionPoint
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.SectionHeader
import com.codyphil.liftlog.ui.components.StatTile
import com.codyphil.liftlog.ui.components.TextPromptDialog
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.launch

enum class Metric(val label: String) { E1RM("Est. 1RM"), TOP("Top weight"), VOLUME("Volume") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(exerciseId: Long, onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val exercise by repo.observeExercise(exerciseId).collectAsStateWithLifecycle(null)
    val rows by repo.observeExerciseHistory(exerciseId).collectAsStateWithLifecycle(emptyList())
    var metric by rememberSaveable { mutableStateOf(Metric.E1RM) }
    var renaming by remember { mutableStateOf(false) }
    val unit = settings.unit
    val points = remember(rows) { ExerciseAnalytics.perSession(rows) }
    val prs = remember(rows) { ExerciseAnalytics.records(rows) }

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
                actions = { IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename exercise", tint = Brand.TextMid) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Eyebrow("Exercise", color = Brand.Volt)
                Text(exercise?.name ?: "", style = MaterialTheme.typography.headlineLarge)
            }
            if (points.isEmpty()) {
                item { Text("No finished workouts with this exercise yet.", color = Brand.TextMid) }
                return@LazyColumn
            }
            item {
                Panel(contentPadding = PaddingValues(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric.entries.forEach { m ->
                            FilterChip(
                                selected = metric == m, onClick = { metric = m }, label = { Text(m.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Brand.Volt, selectedLabelColor = Brand.Ink,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val latest = points.last().value(metric)
                    val first = points.first().value(metric)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Units.format(latest, unit), style = MaterialTheme.typography.displayMedium.merge(Numeric))
                        Spacer(Modifier.width(6.dp))
                        Text(unit.label, style = MaterialTheme.typography.titleMedium, color = Brand.TextMid,
                            modifier = Modifier.padding(bottom = 10.dp))
                        Spacer(Modifier.weight(1f))
                        if (points.size > 1) {
                            val delta = Units.fromKg(latest - first, unit)
                            Text(
                                (if (delta >= 0) "▲ " else "▼ ") + Units.formatNumber(kotlin.math.abs(delta)) + " ${unit.label}",
                                color = if (delta >= 0) Brand.Volt else Brand.Ember,
                                style = MaterialTheme.typography.titleSmall.merge(Numeric),
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    if (points.size > 1) {
                        LineChart(
                            points = points.map { ChartPoint(it.date, it.value(metric)) },
                            formatY = { Units.formatWithUnit(it, unit) },
                            formatX = Format::dateShort,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Log this exercise again to see a trend line.", color = Brand.TextLow, style = MaterialTheme.typography.bodySmall)
                    }
                    if (metric == Metric.E1RM) {
                        Text(
                            "Estimated with the Epley formula from your best set each workout. Less accurate above ~10 reps.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                        )
                    }
                }
            }
            item {
                SectionHeader("Personal records", action = { Icon(Icons.Rounded.EmojiEvents, null, tint = Brand.Ember) })
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        prs.heaviest?.let {
                            PrTile("Heaviest", Units.format(it.weightKg, unit), unit.label, "× ${it.reps} · ${Format.dateShort(it.startedAt)}", Modifier.weight(1f))
                        }
                        prs.bestE1rm?.let { (row, v) ->
                            PrTile("Best e1RM", Units.format(v, unit), unit.label,
                                "${Units.format(row.weightKg, unit)}×${row.reps} · ${Format.dateShort(row.startedAt)}", Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        prs.bestVolume?.let {
                            PrTile("Best volume", Units.formatVolume(it.volumeKg, unit).substringBeforeLast(' '), unit.label,
                                Format.dateShort(it.date), Modifier.weight(1f))
                        }
                        prs.mostReps?.let {
                            PrTile("Most reps", it.reps.toString(), "reps",
                                "@ ${Units.formatWithUnit(it.weightKg, unit)}", Modifier.weight(1f))
                        }
                    }
                }
            }
            item { SectionHeader("Every workout") }
            items(points.reversed(), key = { it.sessionId }) { p -> SessionRow(p, unit) { onOpenSession(p.sessionId) } }
        }
    }

    if (renaming) {
        TextPromptDialog(title = "Rename exercise", initial = exercise?.name.orEmpty(), onDismiss = { renaming = false }) { name ->
            renaming = false
            scope.launch {
                if (!repo.renameExercise(exerciseId, name)) {
                    Toast.makeText(context, "An exercise named \"$name\" already exists", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun SessionPoint.value(metric: Metric) = when (metric) {
    Metric.E1RM -> bestE1rmKg
    Metric.TOP -> topWeightKg
    Metric.VOLUME -> volumeKg
}

@Composable
private fun PrTile(label: String, value: String, unit: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        StatTile(label, value, Modifier.fillMaxWidth(), accent = Brand.Ember, unit = unit)
        Text(sub, style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Brand.TextLow, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
    }
}

@Composable
private fun SessionRow(p: SessionPoint, unit: WeightUnit, onClick: () -> Unit) {
    Panel(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Format.weekdayDate(p.date), style = MaterialTheme.typography.titleSmall)
                Text(
                    p.sets.joinToString("  ") { "${Units.format(it.weightKg, unit)}×${it.reps}" },
                    style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Brand.TextMid,
                )
            }
            Text(Units.formatVolume(p.volumeKg, unit), style = MaterialTheme.typography.labelMedium.merge(Numeric), color = Brand.Ice)
        }
    }
}
