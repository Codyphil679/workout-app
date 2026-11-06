package com.codyphil.liftlog.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.SessionExerciseWithSets
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Stats
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.ConfirmDialog
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.ui.components.StatTile
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit, onOpenExercise: (Long) -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val detail by remember(sessionId) { repo.observeSessionDetail(sessionId) }.collectAsStateWithLifecycle(null)
    var confirmDelete by remember { mutableStateOf(false) }
    // Saveable so a back-stack entry whose data was deleted closes itself instead of rendering blank.
    var loaded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(detail) { if (detail != null) loaded = true else if (loaded) onBack() }
    val d = detail ?: return
    val s = d.session
    val unit = settings.unit
    // Totals count working sets only; warm-ups are shown but don't inflate sets or volume.
    val allSets = d.exercises.flatMap { it.sets }.filter { it.completed && it.setType != SetType.WARMUP }

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
                actions = {
                    if (s.endedAt != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete workout", tint = Brand.TextMid) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Eyebrow(Format.weekdayDate(s.startedAt) + " · " + Format.time(s.startedAt), color = Brand.Volt)
                Text(s.name, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Duration", s.endedAt?.let { Format.durationShort(it - s.startedAt) } ?: "Live", Modifier.weight(1f), accent = Brand.Volt)
                    StatTile("Sets", allSets.size.toString(), Modifier.weight(1f))
                    StatTile("Volume", compact(Units.fromKg(allSets.sumOf { it.weightKg * it.reps }, unit)), Modifier.weight(1f), unit = unit.label)
                }
                if (s.readiness != null || s.intensity < 0.999) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.readiness?.let { Pill("Readiness ${Units.formatNumber(Math.round(it * 10) / 10.0)}/5", color = Brand.Ice) }
                        if (s.intensity < 0.999) Pill("Lighter day −${Math.round((1 - s.intensity) * 100)}%", color = Brand.Ember)
                    }
                }
                if (s.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(s.notes, color = Brand.TextMid)
                }
            }
            items(d.exercises, key = { it.row.id }) { ex -> ExerciseBlock(ex, unit) { onOpenExercise(ex.row.exerciseId) } }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this workout?",
            message = "It will be removed from your history and progress charts. This can't be undone.",
            confirmText = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { repo.deleteSession(sessionId); container.deleteFromHealth(sessionId) }
            },
        )
    }
}

@Composable
private fun ExerciseBlock(ex: SessionExerciseWithSets, unit: WeightUnit, onOpen: () -> Unit) {
    val sets = ex.sets.filter { it.completed }
    val working = sets.filter { it.setType != SetType.WARMUP }
    val best = working.maxByOrNull { Stats.e1rm(it.weightKg, it.reps) }
    Panel(contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.row.exerciseName, style = MaterialTheme.typography.titleLarge)
                if (best != null) {
                    Text(
                        "Best set e1RM ${Units.formatWhole(Stats.e1rm(best.weightKg, best.reps), unit)} ${unit.label}",
                        style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Brand.TextMid,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Progress", tint = Brand.TextLow)
        }
        ex.row.note?.takeIf { it.startsWith("Training max") && it.contains("→") }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = if (it.contains("missed") || it.contains(" of ")) Brand.Ember else Brand.Volt)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(bottom = 4.dp)) {
            Eyebrow("Set", modifier = Modifier.width(44.dp))
            Eyebrow("Weight × reps", modifier = Modifier.weight(1f))
            Eyebrow("Rest before", modifier = Modifier.width(96.dp))
        }
        sets.forEach { set ->
            val (marker, markerColor) = when (set.setType) {
                SetType.WARMUP -> "W" to Brand.Ember
                SetType.FAILURE -> "F" to Brand.Danger
                SetType.DROP -> "D" to Brand.Ice
                SetType.NORMAL -> ((working.indexOf(set) + 1).toString() + if (set.amrap) "+" else "") to
                    (if (set.amrap) Brand.Volt else Brand.TextMid)
            }
            val isWarmup = set.setType == SetType.WARMUP
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Brand.PanelHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(marker, style = MaterialTheme.typography.labelLarge.merge(Numeric), color = markerColor)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "${Units.format(set.weightKg, unit)} ${unit.label} × ${set.reps}" + (set.rpe?.let { "  @${Units.formatNumber(it)}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium.merge(Numeric),
                    color = if (isWarmup) Brand.TextLow else Brand.TextHi,
                    modifier = Modifier.weight(1f),
                )
                if (set == best && working.size > 1) {
                    Icon(Icons.Rounded.EmojiEvents, "Top set", tint = Brand.Ember, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    set.restBeforeSeconds?.let { Format.duration(it.toLong()) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium.merge(Numeric),
                    color = Brand.Ice,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
    }
}
