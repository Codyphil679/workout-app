package com.codyphil.liftlog.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.DayExerciseWithName
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.ExercisePickerSheet
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.NumberField
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.ui.components.Stepper
import com.codyphil.liftlog.ui.components.TextPromptDialog
import com.codyphil.liftlog.ui.theme.Brand
import kotlinx.coroutines.launch

private data class EditTarget(val exerciseId: Long, val name: String, val existing: DayExerciseWithName?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(dayId: Long, onBack: () -> Unit, onStartWorkout: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val day by repo.observeDay(dayId).collectAsStateWithLifecycle(null)
    val items by repo.observeDayExercises(dayId).collectAsStateWithLifecycle(emptyList())
    val exercises by repo.observeExercises().collectAsStateWithLifecycle(emptyList())
    val active by repo.observeActiveSession().collectAsStateWithLifecycle(null)
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(day) { if (day != null) loaded = true else if (loaded) onBack() }
    val d = day ?: return

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
                actions = { TextButton(onClick = { renaming = true }) { Text("Rename") } },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = { scope.launch { repo.startFromDay(dayId); onStartWorkout() } },
                        enabled = active == null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (active == null) "Start this workout" else "Finish your current workout first",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Eyebrow("Training day", color = Brand.Volt)
                Text(d.name, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                DayExerciseRow(
                    item = item, unit = settings.unit, isFirst = index == 0, isLast = index == items.lastIndex,
                    onEdit = { editing = EditTarget(item.exerciseId, item.exerciseName, item) },
                    onMove = { delta -> scope.launch { repo.moveDayExercise(item.id, dayId, delta) } },
                    onDelete = { scope.launch { repo.deleteDayExercise(item.id) } },
                )
            }
            item {
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
            }
        }
    }

    if (picker) {
        ExercisePickerSheet(
            exercises = exercises,
            onDismiss = { picker = false },
            onPick = { picker = false; editing = EditTarget(it.id, it.name, null) },
            onCreate = { name ->
                picker = false
                scope.launch { editing = EditTarget(repo.getOrCreateExercise(name), name, null) }
            },
            onDelete = { ex -> scope.launch { repo.deleteExerciseIfUnused(ex.id) } },
        )
    }
    editing?.let { target ->
        TargetDialog(
            title = target.name,
            unit = settings.unit,
            initialSets = target.existing?.targetSets ?: 3,
            initialReps = target.existing?.targetReps ?: 10,
            initialWeightKg = target.existing?.targetWeightKg ?: 0.0,
            initialRest = target.existing?.restSeconds ?: settings.defaultRestSeconds,
            onDismiss = { editing = null },
            onSave = { sets, reps, kg, rest ->
                editing = null
                scope.launch {
                    if (target.existing == null) repo.addDayExercise(dayId, target.exerciseId, sets, reps, kg, rest)
                    else repo.updateDayExercise(target.existing.id, dayId, sets, reps, kg, rest)
                }
            },
        )
    }
    if (renaming) {
        TextPromptDialog(title = "Rename day", initial = d.name, onDismiss = { renaming = false }) {
            renaming = false; scope.launch { repo.renameDay(dayId, it) }
        }
    }
}

@Composable
private fun DayExerciseRow(
    item: DayExerciseWithName,
    unit: WeightUnit,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Panel(onClick = onEdit, contentPadding = PaddingValues(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.exerciseName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${item.targetSets} × ${item.targetReps}")
                    if (item.targetWeightKg > 0) Pill(Units.formatWithUnit(item.targetWeightKg, unit), color = Brand.TextHi)
                    Pill("⏱ ${Format.duration(item.restSeconds.toLong())}", color = Brand.Ice)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options", tint = Brand.TextMid) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove", color = Brand.Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun TargetDialog(
    title: String,
    unit: WeightUnit,
    initialSets: Int,
    initialReps: Int,
    initialWeightKg: Double,
    initialRest: Int,
    onDismiss: () -> Unit,
    onSave: (sets: Int, reps: Int, weightKg: Double, restSeconds: Int) -> Unit,
) {
    var sets by remember { mutableIntStateOf(initialSets) }
    var reps by remember { mutableIntStateOf(initialReps) }
    var rest by remember { mutableIntStateOf(initialRest) }
    var weight by remember { mutableStateOf(if (initialWeightKg > 0) Units.format(initialWeightKg, unit) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Stepper("Sets", sets, { sets = it }, min = 1, max = 20)
                    Stepper("Reps", reps, { reps = it }, min = 1, max = 100)
                }
                NumberField(weight, { weight = it }, label = "Starting weight", suffix = unit.label, modifier = Modifier.fillMaxWidth())
                Text(
                    "Used the first time. After that, each workout pre-fills with what you lifted last time.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                )
                Stepper("Rest between sets", rest, { rest = it }, step = 15, min = 0, max = 900,
                    display = { Format.duration(it.toLong()) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sets, reps, Units.toKg(Units.parse(weight) ?: 0.0, unit), rest) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

