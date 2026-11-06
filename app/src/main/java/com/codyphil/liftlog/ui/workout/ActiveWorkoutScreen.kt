package com.codyphil.liftlog.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import com.codyphil.liftlog.ui.components.isValidNumberInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.codyphil.liftlog.data.SetType
import com.codyphil.liftlog.ui.components.PlateCalculatorDialog
import com.codyphil.liftlog.ui.components.NumberField
import com.codyphil.liftlog.ui.components.RpeDialog
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.data.SessionExerciseWithSets
import com.codyphil.liftlog.data.SetLog
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.timer.RestTimer
import com.codyphil.liftlog.ui.components.ConfirmDialog
import com.codyphil.liftlog.ui.components.ExercisePickerSheet
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.ui.components.Stepper
import com.codyphil.liftlog.ui.components.TextPromptDialog
import com.codyphil.liftlog.ui.containerViewModel
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(onBack: () -> Unit, onFinished: (Long) -> Unit, onOpenExercise: (Long) -> Unit) {
    val vm = containerViewModel { ActiveWorkoutViewModel(it) }
    val container = com.codyphil.liftlog.ui.appContainer()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lastTime by vm.lastTime.collectAsStateWithLifecycle()
    val rest by RestTimer.state.collectAsStateWithLifecycle()
    val exercises by remember { vm.repo.observeExercises() }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    var showPicker by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var nothingLogged by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var plateFor by remember { mutableStateOf<Double?>(null) }
    var tmFor by remember { mutableStateOf<SessionExerciseWithSets?>(null) }
    val togglesInFlight = remember { mutableSetOf<Long>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val s = state
    LaunchedEffect(s) {
        if (s is WorkoutUiState.NoActiveWorkout && !finishing) onBack()
    }
    if (s !is WorkoutUiState.Active) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val detail = s.detail
    val session = detail.session
    val supersetLetters = detail.exercises.mapNotNull { it.row.supersetGroup }.distinct()
        .mapIndexed { i, g -> g to ('A' + i).toString() }.toMap()
    val doneSets = detail.exercises.sumOf { e -> e.sets.count { it.completed } }
    val totalSets = detail.exercises.sumOf { it.sets.size }

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {
                    Column(Modifier.clickable { renaming = true }) {
                        Text(session.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ElapsedText(session.startedAt, doneSets, totalSets)
                    }
                },
                actions = {
                    Button(
                        onClick = { confirmFinish = true },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(40.dp),
                    ) { Text("Finish", fontWeight = FontWeight.Bold) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename workout") }, onClick = { menuOpen = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Discard workout", color = Brand.Danger) },
                                onClick = { menuOpen = false; confirmDiscard = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().imePadding(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(detail.exercises, key = { _, it -> it.row.id }) { index, ex ->
                    val members = ex.row.supersetGroup?.let { g -> detail.exercises.filter { it.row.supersetGroup == g } }
                    ExerciseCard(
                        ex = ex,
                        unit = settings.unit,
                        trackRpe = settings.trackRpe,
                        supersetLabel = ex.row.supersetGroup?.let { supersetLetters[it] },
                        canLinkNext = index < detail.exercises.lastIndex,
                        lastTime = lastTime[ex.row.exerciseId].orEmpty(),
                        isFirst = index == 0,
                        isLast = index == detail.exercises.lastIndex,
                        onOpenHistory = { onOpenExercise(ex.row.exerciseId) },
                        onMove = { delta -> vm.launch { vm.repo.moveSessionExercise(ex.row.id, delta) } },
                        onRemove = { vm.launch { vm.repo.removeSessionExercise(ex.row.id) } },
                        onSetRest = { secs -> vm.launch { vm.repo.setSessionExerciseRest(ex.row.id, secs) } },
                        onSuperset = { link -> vm.launch { vm.repo.setSessionSuperset(ex.row.id, link) } },
                        onAddSet = { vm.launch { vm.repo.addSet(ex.row.id) } },
                        onWarmups = {
                            vm.launch {
                                if (!vm.repo.addWarmupSets(ex.row.id, settings.unit, settings.bar)) {
                                    Toast.makeText(context, "Enter a working weight heavier than the bar first", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPlates = { plateFor = it },
                        onDeleteSet = { id -> vm.launch { vm.repo.deleteSet(id) } },
                        onSetType = { id, type -> vm.launch { vm.repo.setSetType(id, type) } },
                        onRpe = { id, rpe -> vm.launch { vm.repo.setSetRpe(id, rpe) } },
                        onAmrap = { id, on -> vm.launch { vm.repo.setAmrap(id, on) } },
                        onTrainingMax = { tmFor = ex },
                        onEditSet = { id, w, r -> vm.launch { vm.repo.updateSetValues(id, w, r) } },
                        onToggleSet = { set, w, r ->
                            if (togglesInFlight.add(set.id)) vm.launch {
                                try {
                                if (set.completed) vm.repo.uncompleteSet(set.id)
                                else {
                                    val restSecs = vm.repo.completeSet(set.id, w, r)
                                    val flow = SupersetFlow.afterSet(detail.exercises, ex, set, members)
                                    if (flow.goTo != null) {
                                        val target = detail.exercises.indexOfFirst { it.row.id == flow.goTo.row.id }
                                        if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                                    }
                                    if (restSecs == null) { /* already completed (double tap) or deleted */ }
                                    else if (!flow.rest) RestTimer.skip() // straight into the next superset exercise
                                    else if (settings.autoStartRest) {
                                        val secs = if (set.setType == SetType.WARMUP) minOf(restSecs, 60) else restSecs
                                        RestTimer.start(context, secs, flow.label)
                                    }
                                }
                                } finally { togglesInFlight.remove(set.id) }
                            }
                        },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add exercise")
                    }
                }
            }

            AnimatedVisibility(
                visible = rest != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            ) {
                rest?.let { RestTimerPanel(it, Modifier.fillMaxWidth()) }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = exercises,
            onDismiss = { showPicker = false },
            onPick = { ex ->
                showPicker = false
                vm.launch { vm.repo.addExerciseToSession(session.id, ex.id, settings.defaultRestSeconds) }
            },
            onCreate = { name ->
                showPicker = false
                vm.launch {
                    val id = vm.repo.getOrCreateExercise(name)
                    vm.repo.addExerciseToSession(session.id, id, settings.defaultRestSeconds)
                }
            },
            onDelete = { ex ->
                vm.launch {
                    if (!vm.repo.deleteExerciseIfUnused(ex.id)) {
                        Toast.makeText(context, "${ex.name} is used in a program or workout, so it can't be deleted", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
    tmFor?.let { ex ->
        TrainingMaxDialog(
            exerciseName = ex.row.exerciseName,
            currentKg = ex.row.trainingMaxKg,
            unit = settings.unit,
            suggest = { vm.repo.suggestedTrainingMax(ex.row.exerciseId, settings.unit) },
            onDismiss = { tmFor = null },
            onSave = { kg -> tmFor = null; vm.launch { vm.repo.applyTrainingMax(ex.row.id, kg, settings.unit) } },
        )
    }
    plateFor?.let { w ->
        PlateCalculatorDialog(w, settings.bar, settings.unit, onDismiss = { plateFor = null })
    }
    if (renaming) {
        TextPromptDialog(
            title = "Rename workout", initial = session.name,
            onDismiss = { renaming = false },
            onConfirm = { name -> renaming = false; vm.launch { vm.repo.renameSession(session.id, name) } },
        )
    }
    if (confirmFinish) {
        val remaining = totalSets - doneSets
        ConfirmDialog(
            title = "Finish workout?",
            message = if (remaining > 0) "$remaining unchecked set(s) will be removed from the log." else "Nice work. This will be saved to your history.",
            confirmText = "Finish",
            destructive = false,
            onDismiss = { confirmFinish = false },
            onConfirm = {
                if (finishing) return@ConfirmDialog
                confirmFinish = false
                finishing = true
                vm.launch {
                    if (vm.repo.finishSession(session.id, settings.unit)) {
                        RestTimer.skip()
                        container.syncToHealth(session.id)
                        onFinished(session.id)
                    } else {
                        finishing = false
                        nothingLogged = true
                    }
                }
            },
        )
    }
    if (nothingLogged) {
        AlertDialog(
            onDismissRequest = { nothingLogged = false },
            title = { Text("Nothing logged yet") },
            text = { Text("Check off at least one set to save this workout, or discard it.") },
            confirmButton = { TextButton(onClick = { nothingLogged = false }) { Text("Keep going") } },
            dismissButton = {
                TextButton(onClick = { nothingLogged = false; confirmDiscard = true }) { Text("Discard", color = Brand.Danger) }
            },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            message = "Everything logged in this workout will be deleted. This can't be undone.",
            confirmText = "Discard",
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                RestTimer.skip()
                vm.launch { vm.repo.deleteSession(session.id) }
            },
        )
    }
}

@Composable
private fun ElapsedText(startedAt: Long, done: Int, total: Int) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Text(
        "${Format.duration((now - startedAt) / 1000)} · $done/$total sets",
        style = MaterialTheme.typography.labelMedium.merge(Numeric),
        color = Brand.Volt,
    )
}

/** Decides what happens after a set is checked off: rest now, or move straight to the next exercise of a superset. */
internal object SupersetFlow {
    data class Result(val rest: Boolean, val goTo: SessionExerciseWithSets?, val label: String)

    fun afterSet(
        all: List<SessionExerciseWithSets>,
        current: SessionExerciseWithSets,
        justDone: SetLog,
        members: List<SessionExerciseWithSets>?,
    ): Result {
        fun hasWork(e: SessionExerciseWithSets) = e.sets.any { !it.completed && it.id != justDone.id }
        if (members != null && members.size > 1 && justDone.setType != SetType.WARMUP) {
            val idx = members.indexOfFirst { it.row.id == current.row.id }
            val nextInRound = members.drop(idx + 1).firstOrNull(::hasWork)
            if (nextInRound != null) {
                return Result(rest = false, goTo = nextInRound, label = "Superset: ${nextInRound.row.exerciseName}")
            }
            val roundStart = members.firstOrNull(::hasWork)
            if (roundStart != null) {
                return Result(rest = true, goTo = roundStart, label = "Next round: ${roundStart.row.exerciseName}")
            }
        }
        val nextInSame = current.sets.firstOrNull { !it.completed && it.id != justDone.id }
        if (nextInSame != null) {
            return Result(true, null, "Next: ${current.row.exerciseName} · ${setLabel(current.sets, nextInSame)}")
        }
        val idx = all.indexOfFirst { it.row.id == current.row.id }
        val nextEx = all.drop(idx + 1).firstOrNull(::hasWork) ?: all.firstOrNull { it.row.id != current.row.id && hasWork(it) }
        return if (nextEx != null) Result(true, nextEx, "Next: ${nextEx.row.exerciseName}")
        else Result(true, null, "Last set done. Finish when ready.")
    }
}

/** "Warm-up", "set 3", "drop set"… */
internal fun setLabel(sets: List<SetLog>, set: SetLog): String = when (set.setType) {
    SetType.WARMUP -> "warm-up"
    else -> "set ${sets.filter { it.setType != SetType.WARMUP }.indexOfFirst { it.id == set.id } + 1}"
}

@Composable
private fun ExerciseCard(
    ex: SessionExerciseWithSets,
    unit: WeightUnit,
    trackRpe: Boolean,
    supersetLabel: String?,
    canLinkNext: Boolean,
    lastTime: List<ExerciseSetRow>,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenHistory: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onSetRest: (Int) -> Unit,
    onSuperset: (Boolean) -> Unit,
    onAddSet: () -> Unit,
    onWarmups: () -> Unit,
    onPlates: (Double) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onSetType: (Long, SetType) -> Unit,
    onRpe: (Long, Double?) -> Unit,
    onAmrap: (Long, Boolean) -> Unit,
    onTrainingMax: () -> Unit,
    onEditSet: (Long, Double, Int) -> Unit,
    onToggleSet: (SetLog, Double, Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var editRest by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val allDone = ex.sets.isNotEmpty() && ex.sets.all { it.completed }
    val working = ex.sets.filter { it.setType != SetType.WARMUP }

    Panel(
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        borderColor = if (supersetLabel != null) Brand.Ice.copy(alpha = 0.55f) else Brand.Line.copy(alpha = 0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (supersetLabel != null) Eyebrow("Superset $supersetLabel", color = Brand.Ice)
                Text(
                    ex.row.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (allDone) Brand.Volt else Brand.TextHi,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("⏱ ${Format.duration(ex.row.restSeconds.toLong())}", color = Brand.Ice, modifier = Modifier.clickable { editRest = true })
                    val goal = ex.row.targetReps?.let { min -> ex.row.repMax?.takeIf { it > min }?.let { "$min–$it" } ?: "$min" }
                    goal?.let { Pill("Goal $it reps", color = Brand.TextMid) }
                }
            }
            IconButton(onClick = onOpenHistory) { Icon(Icons.Rounded.History, "History", tint = Brand.TextMid) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Exercise options", tint = Brand.TextMid) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rest time") }, leadingIcon = { Icon(Icons.Rounded.Timer, null) },
                        onClick = { menu = false; editRest = true })
                    DropdownMenuItem(text = { Text("Add warm-up sets") }, onClick = { menu = false; onWarmups() })
                    DropdownMenuItem(
                        text = { Text(if (ex.row.trainingMaxKg != null) "Training max: ${Units.formatWithUnit(ex.row.trainingMaxKg, unit)}" else "Set training max") },
                        onClick = { menu = false; onTrainingMax() },
                    )
                    if (supersetLabel != null) {
                        DropdownMenuItem(text = { Text("Remove from superset") }, onClick = { menu = false; onSuperset(false) })
                    }
                    if (canLinkNext) {
                        DropdownMenuItem(text = { Text("Superset with next exercise") }, onClick = { menu = false; onSuperset(true) })
                    }
                    if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove exercise", color = Brand.Danger) }, onClick = { menu = false; confirmRemove = true })
                }
            }
        }

        ex.row.note?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (note.startsWith("+")) Brand.Volt else if (note.startsWith("Deload")) Brand.Ember else Brand.TextMid,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
        }
        if (lastTime.isNotEmpty()) {
            Text(
                "Last time (${Format.dateShort(lastTime.first().startedAt)}): " +
                    lastTime.joinToString("  ") { "${Units.format(it.weightKg, unit)}×${it.reps}" },
                style = MaterialTheme.typography.bodySmall.merge(Numeric),
                color = Brand.TextLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        val widths = cellWidths(trackRpe)
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Set", modifier = Modifier.width(40.dp))
            Eyebrow("Prev", modifier = Modifier.weight(1f))
            Eyebrow(unit.label, modifier = Modifier.width(widths.weight).padding(start = 4.dp))
            Spacer(Modifier.width(6.dp))
            Eyebrow("Reps", modifier = Modifier.width(widths.reps).padding(start = 4.dp))
            if (trackRpe) {
                Spacer(Modifier.width(6.dp))
                Eyebrow("RPE", modifier = Modifier.width(widths.rpe).padding(start = 4.dp))
            }
            Spacer(Modifier.width(50.dp))
        }
        ex.sets.forEach { set ->
            val workingIndex = working.indexOfFirst { it.id == set.id }
            val prev = if (workingIndex >= 0) lastTime.getOrNull(workingIndex) else null
            SetRow(
                set, if (workingIndex >= 0) workingIndex + 1 else 0, prev, unit, trackRpe, widths,
                onDelete = { onDeleteSet(set.id) },
                onSetType = { onSetType(set.id, it) },
                onRpe = { onRpe(set.id, it) },
                onAmrap = { onAmrap(set.id, it) },
                onEdit = onEditSet, onToggle = onToggleSet,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onAddSet) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add set")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onWarmups) { Text("Warm-up", color = Brand.TextMid) }
            TextButton(onClick = {
                val next = working.firstOrNull { !it.completed } ?: working.lastOrNull()
                onPlates(next?.weightKg?.let { Units.fromKg(it, unit) } ?: 0.0)
            }) { Text("Plates", color = Brand.TextMid) }
        }
    }

    if (editRest) {
        RestDialog(ex.row.restSeconds, onDismiss = { editRest = false }, onSave = { editRest = false; onSetRest(it) })
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove ${ex.row.exerciseName}?",
            message = "Its sets in this workout will be deleted.",
            confirmText = "Remove",
            onDismiss = { confirmRemove = false },
            onConfirm = { confirmRemove = false; onRemove() },
        )
    }
}

private data class CellWidths(val weight: Dp, val reps: Dp, val rpe: Dp)
private fun cellWidths(trackRpe: Boolean) =
    if (trackRpe) CellWidths(68.dp, 50.dp, 46.dp) else CellWidths(80.dp, 60.dp, 0.dp)

@Composable
private fun SetRow(
    set: SetLog,
    workingNumber: Int,
    prev: ExerciseSetRow?,
    unit: WeightUnit,
    trackRpe: Boolean,
    widths: CellWidths,
    onDelete: () -> Unit,
    onSetType: (SetType) -> Unit,
    onRpe: (Double?) -> Unit,
    onAmrap: (Boolean) -> Unit,
    onEdit: (Long, Double, Int) -> Unit,
    onToggle: (SetLog, Double, Int) -> Unit,
) {
    var weight by remember(set.id, unit) { mutableStateOf(if (set.weightKg == 0.0) "" else Units.format(set.weightKg, unit)) }
    var reps by remember(set.id) { mutableStateOf(if (set.reps == 0) "" else set.reps.toString()) }
    var weightFocused by remember { mutableStateOf(false) }
    var repsFocused by remember { mutableStateOf(false) }
    // While you're typing, the field is the source of truth. Otherwise follow the database, so changes made
    // elsewhere (training max recalculation, "Prev" copy, warm-ups) show up instead of being overwritten.
    LaunchedEffect(set.weightKg, weightFocused, unit) {
        if (!weightFocused && Units.toKg(Units.parse(weight) ?: 0.0, unit).let { kotlin.math.abs(it - set.weightKg) > 1e-6 }) {
            weight = if (set.weightKg == 0.0) "" else Units.format(set.weightKg, unit)
        }
    }
    LaunchedEffect(set.reps, repsFocused) {
        if (!repsFocused && (reps.toIntOrNull() ?: 0) != set.reps) reps = if (set.reps == 0) "" else set.reps.toString()
    }
    var menu by remember { mutableStateOf(false) }
    var rpeDialog by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val bg by animateColorAsState(if (set.completed) Brand.Volt.copy(alpha = 0.10f) else Color.Transparent, label = "rowBg")

    fun weightKg() = Units.toKg(Units.parse(weight) ?: 0.0, unit)
    fun repsInt() = reps.toIntOrNull() ?: 0
    // Empty is fine (bodyweight moves), but something like "." must be fixed before the set can be checked off.
    val weightInvalid = weight.isNotBlank() && Units.parse(weight) == null

    val (marker, markerColor) = when (set.setType) {
        SetType.WARMUP -> "W" to Brand.Ember
        SetType.FAILURE -> "F" to Brand.Danger
        SetType.DROP -> "D" to Brand.Ice
        SetType.NORMAL -> (workingNumber.toString() + if (set.amrap) "+" else "") to
            (if (set.completed || set.amrap) Brand.Volt else Brand.TextMid)
    }

    Row(
        Modifier.fillMaxWidth().padding(end = 8.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(12.dp)).background(bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp)) {
            Text(
                marker,
                style = MaterialTheme.typography.titleMedium.merge(Numeric),
                color = markerColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 10.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SetType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.label, fontWeight = if (t == set.setType) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = { if (t == set.setType) Icon(Icons.Rounded.Check, null, tint = Brand.Volt) },
                        onClick = { menu = false; onSetType(t) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (set.amrap) "AMRAP: on" else "AMRAP (as many reps as possible)") },
                    leadingIcon = { if (set.amrap) Icon(Icons.Rounded.Check, null, tint = Brand.Volt) },
                    onClick = { menu = false; onAmrap(!set.amrap) },
                )
                DropdownMenuItem(text = { Text("Delete set", color = Brand.Danger) }, onClick = { menu = false; onDelete() })
            }
        }
        Text(
            prev?.let { "${Units.format(it.weightKg, unit)}×${it.reps}" } ?: "—",
            style = MaterialTheme.typography.bodySmall.merge(Numeric),
            color = Brand.TextLow,
            modifier = Modifier.weight(1f).clickable(enabled = prev != null && !set.completed) {
                prev?.let {
                    weight = Units.format(it.weightKg, unit); reps = it.reps.toString()
                    onEdit(set.id, it.weightKg, it.reps)
                }
            },
            maxLines = 1,
        )
        CellField(weight, { weight = it; onEdit(set.id, weightKg(), repsInt()) }, decimal = true, enabled = !set.completed,
            placeholder = "0", modifier = Modifier.width(widths.weight), onFocus = { weightFocused = it },
            error = weightInvalid)
        Spacer(Modifier.width(6.dp))
        CellField(reps, { reps = it; onEdit(set.id, weightKg(), repsInt()) }, decimal = false, enabled = !set.completed,
            onFocus = { repsFocused = it },
            placeholder = set.targetReps?.let { "$it+" } ?: "0", modifier = Modifier.width(widths.reps))
        if (trackRpe) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.width(widths.rpe).height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (set.rpe != null) Brand.Ember.copy(alpha = 0.14f) else Brand.PanelHigh)
                    .clickable { rpeDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    set.rpe?.let { Units.formatNumber(it) } ?: "–",
                    style = MaterialTheme.typography.titleSmall.merge(Numeric),
                    color = if (set.rpe != null) Brand.Ember else Brand.TextLow,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        val canComplete = set.completed || (repsInt() > 0 && !weightInvalid)
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (set.completed) Brand.Volt else Brand.PanelHigher)
                .clickable(enabled = canComplete) { focus.clearFocus(); onToggle(set, weightKg(), repsInt()) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check, contentDescription = if (set.completed) "Mark not done" else "Complete set",
                tint = when {
                    set.completed -> Brand.Ink
                    canComplete -> Brand.TextHi
                    else -> Brand.TextLow
                },
            )
        }
    }
    if (rpeDialog) {
        RpeDialog(set.rpe, onDismiss = { rpeDialog = false }, onPick = { rpeDialog = false; onRpe(it) })
    }
}

@Composable
private fun CellField(
    value: String,
    onChange: (String) -> Unit,
    decimal: Boolean,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
    error: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = { new -> if (isValidNumberInput(new, decimal, maxLength = 6)) onChange(new) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.merge(Numeric).copy(
            color = if (enabled) Brand.TextHi else Brand.Volt, textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(Brand.Volt),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.height(44.dp).onFocusChanged { onFocus(it.isFocused) },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().clip(shape)
                    .background(if (enabled) Brand.PanelHigh else Color.Transparent)
                    .border(1.dp, if (!enabled) Color.Transparent else if (error) Brand.Danger else Brand.Line, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) Text(placeholder, color = Brand.TextLow, style = MaterialTheme.typography.titleMedium)
                inner()
            }
        },
    )
}

@Composable
fun RestDialog(initialSeconds: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var secs by remember { mutableIntStateOf(initialSeconds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest time") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Stepper("Seconds", secs, { secs = it }, step = 15, min = 0, max = 900, display = { Format.duration(it.toLong()) })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 90, 120, 180, 300).forEach { p ->
                        Pill(Format.duration(p.toLong()), color = if (p == secs) Brand.Volt else Brand.TextMid,
                            modifier = Modifier.clickable { secs = p })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(secs) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TrainingMaxDialog(
    exerciseName: String,
    currentKg: Double?,
    unit: WeightUnit,
    suggest: suspend () -> Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(currentKg?.let { Units.format(it, unit) } ?: "") }
    var suggestion by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { suggestion = suggest() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$exerciseName training max") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(text, { text = it }, label = "Training max", suffix = unit.label, modifier = Modifier.fillMaxWidth())
                Text(
                    "Percentages are taken from this. A common choice is about 90% of your true 1-rep max.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextMid,
                )
                suggestion?.let { kg ->
                    Pill("Use ${Units.formatWithUnit(kg, unit)} (90% of your best est. 1RM)", color = Brand.Ice,
                        modifier = Modifier.clickable { text = Units.format(kg, unit) })
                }
            }
        },
        confirmButton = {
            val v = Units.parse(text)
            TextButton(enabled = v != null && v > 0, onClick = { onSave(Units.toKg(v!!, unit)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
