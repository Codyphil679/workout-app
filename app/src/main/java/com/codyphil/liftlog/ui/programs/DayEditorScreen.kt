package com.codyphil.liftlog.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.codyphil.liftlog.data.Progression
import com.codyphil.liftlog.domain.PercentScheme
import com.codyphil.liftlog.domain.PercentSet
import com.codyphil.liftlog.domain.Progressions
import kotlin.math.roundToInt
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
import com.codyphil.liftlog.ui.components.rememberDayStarter
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
    val startDay = rememberDayStarter(onStarted = onStartWorkout)
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
                        onClick = { startDay(dayId) },
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
            val letters = items.mapNotNull { it.supersetGroup }.distinct().mapIndexed { i, g -> g to ('A' + i).toString() }.toMap()
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                DayExerciseRow(
                    item = item, unit = settings.unit, isFirst = index == 0, isLast = index == items.lastIndex,
                    supersetLabel = item.supersetGroup?.let { letters[it] },
                    onEdit = { editing = EditTarget(item.exerciseId, item.exerciseName, item) },
                    onMove = { delta -> scope.launch { repo.moveDayExercise(item.id, dayId, delta) } },
                    onSuperset = { link -> scope.launch { repo.setDaySuperset(item.id, dayId, link) } },
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
        val e = target.existing
        val defaults = Progressions.defaultFor(target.name, if (e == null) 8 else e.targetReps, settings.unit)
        val tm = exercises.firstOrNull { it.id == target.exerciseId }?.trainingMaxKg
        val initial = e?.let { TargetValues(it.targetSets, it.targetReps, it.repMax ?: (it.targetReps + 4), it.targetWeightKg, it.restSeconds,
            it.progression, it.incrementKg.takeIf { inc -> inc > 0 } ?: defaults.incrementKg, it.autoWarmup,
            it.percentScheme ?: PercentScheme.presets.first().second, tm) }
            ?: if (defaults.progression == Progression.LINEAR) {
                TargetValues(3, 5, 9, 0.0, maxOf(settings.defaultRestSeconds, 150), Progression.LINEAR, defaults.incrementKg, true,
                    PercentScheme.presets.first().second, tm)
            } else {
                TargetValues(3, 8, defaults.repMax ?: 12, 0.0, settings.defaultRestSeconds, Progression.DOUBLE, defaults.incrementKg, false,
                    PercentScheme.presets.first().second, tm)
            }
        TargetDialog(
            title = target.name,
            unit = settings.unit,
            initial = initial,
            onDismiss = { editing = null },
            onSave = { v ->
                editing = null
                val repMax = if (v.progression == Progression.DOUBLE) v.repMax else null
                val scheme = if (v.progression == Progression.PERCENT) v.percentScheme else null
                val parsed = PercentScheme.parse(scheme)
                val sets = if (parsed.isNotEmpty()) parsed.size else v.sets
                val reps = parsed.firstOrNull()?.reps ?: v.reps
                scope.launch {
                    if (v.progression == Progression.PERCENT && v.trainingMaxKg != null) {
                        repo.setTrainingMax(target.exerciseId, v.trainingMaxKg)
                    }
                    if (e == null) repo.addDayExercise(dayId, target.exerciseId, sets, reps, v.weightKg, v.rest,
                        v.progression, v.incrementKg, repMax, v.autoWarmup, percentScheme = scheme)
                    else repo.updateDayExercise(e.id, dayId, sets, reps, v.weightKg, v.rest,
                        v.progression, v.incrementKg, repMax, v.autoWarmup, percentScheme = scheme)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayExerciseRow(
    item: DayExerciseWithName,
    unit: WeightUnit,
    isFirst: Boolean,
    isLast: Boolean,
    supersetLabel: String?,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onSuperset: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Panel(
        onClick = onEdit,
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        borderColor = if (supersetLabel != null) Brand.Ice.copy(alpha = 0.55f) else Brand.Line.copy(alpha = 0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (supersetLabel != null) Eyebrow("Superset $supersetLabel", color = Brand.Ice)
                Text(item.exerciseName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.progression == Progression.PERCENT) {
                        Pill(PercentScheme.describe(PercentScheme.parse(item.percentScheme)))
                        Pill(item.trainingMaxKg?.let { "TM ${Units.formatWithUnit(it, unit)}" } ?: "TM not set", color = Brand.Ember)
                    } else {
                        val reps = if (item.progression == Progression.DOUBLE && item.repMax != null) "${item.targetReps}–${item.repMax}"
                        else "${item.targetReps}"
                        Pill("${item.targetSets} × $reps")
                        if (item.targetWeightKg > 0) Pill(Units.formatWithUnit(item.targetWeightKg, unit), color = Brand.TextHi)
                    }
                    Pill("⏱ ${Format.duration(item.restSeconds.toLong())}", color = Brand.Ice)
                    if (item.progression == Progression.LINEAR || item.progression == Progression.DOUBLE) {
                        Pill("${item.progression.label} +${Units.formatWithUnit(item.incrementKg, unit)}", color = Brand.Ember)
                    }
                    if (item.autoWarmup) Pill("Warm-ups", color = Brand.TextMid)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options", tint = Brand.TextMid) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    if (supersetLabel != null) DropdownMenuItem(text = { Text("Remove from superset") }, onClick = { menu = false; onSuperset(false) })
                    if (!isLast) DropdownMenuItem(text = { Text("Superset with next exercise") }, onClick = { menu = false; onSuperset(true) })
                    if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove", color = Brand.Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

private data class TargetValues(
    val sets: Int,
    val reps: Int,
    val repMax: Int,
    val weightKg: Double,
    val rest: Int,
    val progression: Progression,
    val incrementKg: Double,
    val autoWarmup: Boolean,
    val percentScheme: String,
    val trainingMaxKg: Double?,
)

@Composable
private fun TargetDialog(
    title: String,
    unit: WeightUnit,
    initial: TargetValues,
    onDismiss: () -> Unit,
    onSave: (TargetValues) -> Unit,
) {
    var sets by remember { mutableIntStateOf(initial.sets) }
    var reps by remember { mutableIntStateOf(initial.reps) }
    var repMax by remember { mutableIntStateOf(maxOf(initial.repMax, initial.reps)) }
    var rest by remember { mutableIntStateOf(initial.rest) }
    var progression by remember { mutableStateOf(initial.progression) }
    var autoWarmup by remember { mutableStateOf(initial.autoWarmup) }
    var weight by remember { mutableStateOf(if (initial.weightKg > 0) Units.format(initial.weightKg, unit) else "") }
    var scheme by remember { mutableStateOf(PercentScheme.parse(initial.percentScheme)) }
    var tmText by remember { mutableStateOf(initial.trainingMaxKg?.let { Units.format(it, unit) } ?: "") }
    // Increment is edited in the display unit, in 2.5 lb / 1.25 kg steps.
    val incStep = if (unit == WeightUnit.LB) 2.5 else 1.25
    var incSteps by remember {
        mutableIntStateOf((Units.fromKg(initial.incrementKg, unit) / incStep).roundToInt().coerceAtLeast(1))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Eyebrow("Progression")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Progression.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = progression == p,
                            onClick = { progression = p },
                            shape = SegmentedButtonDefaults.itemShape(i, Progression.entries.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Brand.Volt, activeContentColor = Brand.Ink),
                            icon = {},
                        ) { Text(p.label, maxLines = 1) }
                    }
                }
                val incLabel = Units.formatNumber(incSteps * incStep) + " " + unit.label
                Text(
                    when (progression) {
                        Progression.NONE -> "Each workout starts with what you lifted last time."
                        Progression.LINEAR -> "Hit $sets×$reps → add $incLabel next time. Miss two workouts in a row → drop 10%."
                        Progression.DOUBLE -> "Work from $reps up to $repMax reps. When every set hits $repMax → add $incLabel and go back to $reps."
                        Progression.PERCENT -> "Each set is a % of your training max (TM). A “+” set is as many reps as possible: " +
                            "miss its target → TM −10%; hit it → TM goes up; beat it by 5+ reps → bigger jump."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextMid,
                )
                if (progression == Progression.PERCENT) {
                    PercentEditor(scheme, { scheme = it })
                    NumberField(tmText, { tmText = it }, label = "Training max", suffix = unit.label, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Usually about 90% of your true 1-rep max. Leave empty and it's set from your history the first time.",
                        style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                    )
                } else Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Stepper("Sets", sets, { sets = it }, min = 1, max = 20)
                    Stepper(if (progression == Progression.DOUBLE) "Min reps" else "Reps", reps,
                        { reps = it; if (repMax < it) repMax = it }, min = 1, max = 100)
                }
                if (progression == Progression.DOUBLE) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Stepper("Max reps", repMax, { repMax = it.coerceAtLeast(reps) }, min = 1, max = 100)
                        Stepper("Add", incSteps, { incSteps = it }, min = 1, max = 40,
                            display = { Units.formatNumber(it * incStep) })
                    }
                } else if (progression == Progression.LINEAR) {
                    Stepper("Add each time (${unit.label})", incSteps, { incSteps = it }, min = 1, max = 40,
                        display = { Units.formatNumber(it * incStep) }, modifier = Modifier.fillMaxWidth())
                }
                if (progression != Progression.PERCENT) {
                    NumberField(weight, { weight = it }, label = "Starting weight", suffix = unit.label, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Used the first time only. After that, progression decides.",
                        style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                    )
                }
                Stepper("Rest between sets", rest, { rest = it }, step = 15, min = 0, max = 900,
                    display = { Format.duration(it.toLong()) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add warm-up sets", style = MaterialTheme.typography.titleSmall)
                        Text("Bar, ~40%, 60%, 80% before your first working set.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Switch(
                        checked = autoWarmup, onCheckedChange = { autoWarmup = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.Volt, checkedThumbColor = Brand.Ink),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    TargetValues(
                        sets, reps, repMax, Units.toKg(Units.parse(weight) ?: 0.0, unit), rest, progression,
                        Units.toKg(incSteps * incStep, unit), autoWarmup,
                        PercentScheme.format(scheme.ifEmpty { PercentScheme.parse(PercentScheme.presets.first().second) }),
                        Units.parse(tmText)?.takeIf { it > 0 }?.let { Units.toKg(it, unit) },
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PercentEditor(sets: List<PercentSet>, onChange: (List<PercentSet>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("Sets")
        sets.forEachIndexed { i, ps ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Brand.TextMid, modifier = Modifier.width(16.dp))
                Stepper("%", ps.percent.roundToInt(), { v -> onChange(sets.toMutableList().also { it[i] = ps.copy(percent = v.toDouble()) }) },
                    step = 5, min = 30, max = 110)
                Stepper("Reps", ps.reps, { v -> onChange(sets.toMutableList().also { it[i] = ps.copy(reps = v) }) }, min = 1, max = 30)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("+")
                    Spacer(Modifier.height(6.dp))
                    Pill(if (ps.amrap) "AMRAP" else "—", color = if (ps.amrap) Brand.Volt else Brand.TextLow,
                        modifier = Modifier.clickable { onChange(sets.toMutableList().also { it[i] = ps.copy(amrap = !ps.amrap) }) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(sets + (sets.lastOrNull()?.copy(amrap = false) ?: PercentSet(75.0, 5))) }) { Text("+ Set") }
            if (sets.size > 1) TextButton(onClick = { onChange(sets.dropLast(1)) }) { Text("− Set", color = Brand.TextMid) }
        }
        Eyebrow("Presets")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PercentScheme.presets.forEach { (name, value) ->
                Pill(name, color = Brand.Ice, modifier = Modifier.clickable { onChange(PercentScheme.parse(value)) })
            }
        }
    }
}
