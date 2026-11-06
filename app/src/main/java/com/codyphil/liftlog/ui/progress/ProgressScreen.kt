package com.codyphil.liftlog.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.BodyMetric
import com.codyphil.liftlog.data.Muscle
import com.codyphil.liftlog.data.MuscleSets
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.IconBadge
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.SectionHeader
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import java.util.Calendar

/** Weekly working sets per muscle that most hypertrophy guidance lands on. A rule of thumb, not a prescription. */
private const val TARGET_LOW = 10
private const val TARGET_HIGH = 20

@Composable
fun ProgressScreen(onOpenExercise: (Long) -> Unit, onOpenBody: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val list by repo.observeExercisesWithHistory().collectAsStateWithLifecycle(null)
    val weekStart = remember { startOfWeek() }
    val muscleSets by repo.observeMuscleSets(weekStart).collectAsStateWithLifecycle(emptyList())
    val bodyWeights by repo.observeBody(BodyMetric.BODY_WEIGHT).collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    val items = list ?: return
    val filtered = items.filter { it.name.contains(query.trim(), ignoreCase = true) }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Eyebrow("Historian", color = Brand.Volt)
            Text("Progress", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
        }
        item {
            Panel(onClick = onOpenBody, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.MonitorWeight, tint = Brand.Ice, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Body", style = MaterialTheme.typography.titleMedium)
                        val latest = bodyWeights.lastOrNull()
                        Text(
                            latest?.let { "${Units.formatWithUnit(it.value, settings.unit)} · ${Format.dateShort(it.recordedAt)}" }
                                ?: "Log body weight and measurements",
                            style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Brand.TextMid,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.TextLow)
                }
            }
        }
        item { MuscleVolumePanel(muscleSets) }
        item { SectionHeader("Exercises") }
        if (items.isEmpty()) {
            item {
                Text(
                    "Finish a workout and each exercise gets charts and personal records here.",
                    color = Brand.TextMid, style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Search exercises") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(filtered, key = { it.id }) { ex ->
            Panel(onClick = { onOpenExercise(ex.id) }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.ShowChart, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${ex.sessionCount} workout${if (ex.sessionCount == 1) "" else "s"} · last ${Format.dateShort(ex.lastPerformed)}",
                            style = MaterialTheme.typography.bodySmall, color = Brand.TextMid,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.TextLow, modifier = Modifier.padding(end = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MuscleVolumePanel(data: List<MuscleSets>) {
    val byMuscle = data.associate { it.muscle to it.sets }
    val rows = Muscle.entries.filter { it != Muscle.OTHER || (byMuscle[it] ?: 0) > 0 }
    val scaleMax = maxOf(TARGET_HIGH + 4, byMuscle.values.maxOrNull() ?: 0)
    Panel(contentPadding = PaddingValues(16.dp)) {
        Eyebrow("This week", color = Brand.Volt)
        Text("Working sets per muscle", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        rows.forEach { m ->
            val sets = byMuscle[m] ?: 0
            val color = when {
                sets == 0 -> Brand.PanelHigher
                sets < TARGET_LOW -> Brand.Ice
                sets <= TARGET_HIGH -> Brand.Volt
                else -> Brand.Ember
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, style = MaterialTheme.typography.labelMedium, color = Brand.TextMid, modifier = Modifier.width(88.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    // Track + shaded target band
                    Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(Brand.PanelHigh))
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
                        val w = maxWidth
                        Box(
                            Modifier.offset(x = w * (TARGET_LOW.toFloat() / scaleMax))
                                .width(w * ((TARGET_HIGH - TARGET_LOW).toFloat() / scaleMax)).fillMaxHeight()
                                .background(Brand.Volt.copy(alpha = 0.08f))
                        )
                        if (sets > 0) {
                            Box(
                                Modifier.width(w * (sets.toFloat() / scaleMax).coerceAtMost(1f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(7.dp)).background(color)
                            )
                        }
                    }
                }
                Text(
                    sets.toString(), style = MaterialTheme.typography.labelLarge.merge(Numeric),
                    color = if (sets == 0) Brand.TextLow else Brand.TextHi,
                    modifier = Modifier.width(32.dp).padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Shaded band: $TARGET_LOW–$TARGET_HIGH sets a week, a common target for muscle growth. " +
                "Each set counts toward the exercise's main muscle only.",
            style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
        )
    }
}

internal fun startOfWeek(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    if (timeInMillis > System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, -1)
}.timeInMillis
