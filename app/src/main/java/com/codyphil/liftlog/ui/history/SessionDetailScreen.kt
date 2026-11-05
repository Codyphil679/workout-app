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
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Stats
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.ConfirmDialog
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
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
    val detail by repo.observeSessionDetail(sessionId).collectAsStateWithLifecycle(null)
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(detail) { if (detail != null) loaded = true else if (loaded) onBack() }
    val d = detail ?: return
    val s = d.session
    val unit = settings.unit
    val allSets = d.exercises.flatMap { it.sets }.filter { it.completed }

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
            onConfirm = { confirmDelete = false; scope.launch { repo.deleteSession(sessionId) } },
        )
    }
}

@Composable
private fun ExerciseBlock(ex: SessionExerciseWithSets, unit: WeightUnit, onOpen: () -> Unit) {
    val sets = ex.sets.filter { it.completed }
    val best = sets.maxByOrNull { Stats.e1rm(it.weightKg, it.reps) }
    Panel(contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.row.exerciseName, style = MaterialTheme.typography.titleLarge)
                if (best != null) {
                    Text(
                        "Best set e1RM ${Units.formatWithUnit(Stats.e1rm(best.weightKg, best.reps), unit)}",
                        style = MaterialTheme.typography.bodySmall.merge(Numeric), color = Brand.TextMid,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Progress", tint = Brand.TextLow)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(bottom = 4.dp)) {
            Eyebrow("Set", modifier = Modifier.width(44.dp))
            Eyebrow("Weight × reps", modifier = Modifier.weight(1f))
            Eyebrow("Rest before", modifier = Modifier.width(96.dp))
        }
        sets.forEach { set ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Brand.PanelHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(set.setNumber.toString(), style = MaterialTheme.typography.labelLarge.merge(Numeric), color = Brand.TextMid)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "${Units.format(set.weightKg, unit)} ${unit.label} × ${set.reps}",
                    style = MaterialTheme.typography.titleMedium.merge(Numeric),
                    modifier = Modifier.weight(1f),
                )
                if (set == best && sets.size > 1) {
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
