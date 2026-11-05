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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lastTime by vm.lastTime.collectAsStateWithLifecycle()
    val rest by RestTimer.state.collectAsStateWithLifecycle()
    val exercises by vm.repo.observeExercises().collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    var showPicker by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var nothingLogged by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(detail.exercises, key = { it.row.id }) { ex ->
                    ExerciseCard(
                        ex = ex,
                        unit = settings.unit,
                        lastTime = lastTime[ex.row.exerciseId].orEmpty(),
                        isFirst = ex == detail.exercises.first(),
                        isLast = ex == detail.exercises.last(),
                        onOpenHistory = { onOpenExercise(ex.row.exerciseId) },
                        onMove = { delta -> vm.launch { vm.repo.moveSessionExercise(ex.row.id, delta) } },
                        onRemove = { vm.launch { vm.repo.removeSessionExercise(ex.row.id) } },
                        onSetRest = { secs -> vm.launch { vm.repo.setSessionExerciseRest(ex.row.id, secs) } },
                        onAddSet = { vm.launch { vm.repo.addSet(ex.row.id) } },
                        onDeleteSet = { id -> vm.launch { vm.repo.deleteSet(id) } },
                        onEditSet = { id, w, r -> vm.launch { vm.repo.updateSetValues(id, w, r) } },
                        onToggleSet = { set, w, r ->
                            vm.launch {
                                if (set.completed) vm.repo.uncompleteSet(set.id)
                                else {
                                    val restSecs = vm.repo.completeSet(set.id, w, r)
                                    if (restSecs != null && settings.autoStartRest) {
                                        RestTimer.start(context, restSecs, nextUpLabel(detail.exercises, ex, set))
                                    }
                                }
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
            onDelete = { ex -> vm.launch { vm.repo.deleteExerciseIfUnused(ex.id) } },
        )
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
                confirmFinish = false
                finishing = true
                vm.launch {
                    if (vm.repo.finishSession(session.id)) {
                        RestTimer.skip()
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

private fun nextUpLabel(all: List<SessionExerciseWithSets>, current: SessionExerciseWithSets, justDone: SetLog): String {
    val nextInSame = current.sets.firstOrNull { !it.completed && it.id != justDone.id }
    if (nextInSame != null) return "Next: ${current.row.exerciseName} · set ${nextInSame.setNumber}"
    val idx = all.indexOf(current)
    val nextEx = all.drop(idx + 1).firstOrNull { e -> e.sets.any { !it.completed } }
        ?: all.firstOrNull { e -> e != current && e.sets.any { !it.completed } }
    return if (nextEx != null) "Next: ${nextEx.row.exerciseName}" else "Last set done. Finish when ready."
}

@Composable
private fun ExerciseCard(
    ex: SessionExerciseWithSets,
    unit: WeightUnit,
    lastTime: List<ExerciseSetRow>,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenHistory: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onSetRest: (Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (Long) -> Unit,
    onEditSet: (Long, Double, Int) -> Unit,
    onToggleSet: (SetLog, Double, Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var editRest by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val allDone = ex.sets.isNotEmpty() && ex.sets.all { it.completed }

    Panel(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ex.row.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (allDone) Brand.Volt else Brand.TextHi,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("⏱ ${Format.duration(ex.row.restSeconds.toLong())}", color = Brand.Ice, modifier = Modifier.clickable { editRest = true })
                    ex.row.targetReps?.let { Pill("Goal ${it} reps", color = Brand.TextMid) }
                }
            }
            IconButton(onClick = onOpenHistory) { Icon(Icons.Rounded.History, "History", tint = Brand.TextMid) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Exercise options", tint = Brand.TextMid) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rest time") }, leadingIcon = { Icon(Icons.Rounded.Timer, null) },
                        onClick = { menu = false; editRest = true })
                    if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove exercise", color = Brand.Danger) }, onClick = { menu = false; confirmRemove = true })
                }
            }
        }

        if (lastTime.isNotEmpty()) {
            Text(
                "Last time (${Format.dateShort(lastTime.first().startedAt)}): " +
                    lastTime.joinToString("  ") { "${Units.format(it.weightKg, unit)}×${it.reps}" },
                style = MaterialTheme.typography.bodySmall.merge(Numeric),
                color = Brand.TextLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Set", modifier = Modifier.width(40.dp))
            Eyebrow("Prev", modifier = Modifier.weight(1f))
            Eyebrow(unit.label, modifier = Modifier.width(80.dp).padding(start = 4.dp))
            Spacer(Modifier.width(8.dp))
            Eyebrow("Reps", modifier = Modifier.width(60.dp).padding(start = 4.dp))
            Spacer(Modifier.width(52.dp))
        }
        ex.sets.forEach { set ->
            val prev = lastTime.getOrNull(set.setNumber - 1)
            SetRow(set, prev, unit, onDelete = { onDeleteSet(set.id) }, onEdit = onEditSet, onToggle = onToggleSet)
        }
        TextButton(onClick = onAddSet) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add set")
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

@Composable
private fun SetRow(
    set: SetLog,
    prev: ExerciseSetRow?,
    unit: WeightUnit,
    onDelete: () -> Unit,
    onEdit: (Long, Double, Int) -> Unit,
    onToggle: (SetLog, Double, Int) -> Unit,
) {
    var weight by remember(set.id, unit) { mutableStateOf(if (set.weightKg == 0.0) "" else Units.format(set.weightKg, unit)) }
    var reps by remember(set.id) { mutableStateOf(if (set.reps == 0) "" else set.reps.toString()) }
    var menu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val bg by animateColorAsState(if (set.completed) Brand.Volt.copy(alpha = 0.10f) else Color.Transparent, label = "rowBg")

    fun weightKg() = Units.toKg(Units.parse(weight) ?: 0.0, unit)
    fun repsInt() = reps.toIntOrNull() ?: 0

    Row(
        Modifier.fillMaxWidth().padding(end = 8.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(12.dp)).background(bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp)) {
            Text(
                set.setNumber.toString(),
                style = MaterialTheme.typography.titleMedium.merge(Numeric),
                color = if (set.completed) Brand.Volt else Brand.TextMid,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 10.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
            placeholder = "0", modifier = Modifier.width(80.dp))
        Spacer(Modifier.width(8.dp))
        CellField(reps, { reps = it; onEdit(set.id, weightKg(), repsInt()) }, decimal = false, enabled = !set.completed,
            placeholder = "0", modifier = Modifier.width(60.dp))
        Spacer(Modifier.width(8.dp))
        val canComplete = set.completed || repsInt() > 0
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
}

@Composable
private fun CellField(
    value: String,
    onChange: (String) -> Unit,
    decimal: Boolean,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = { new ->
            if (new.length <= 6 && new.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) onChange(new)
        },
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
        modifier = modifier.height(44.dp),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().clip(shape)
                    .background(if (enabled) Brand.PanelHigh else Color.Transparent)
                    .border(1.dp, if (enabled) Brand.Line else Color.Transparent, shape),
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
