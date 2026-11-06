package com.codyphil.liftlog.ui.programs

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.DaySummary
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.ConfirmDialog
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.TextPromptDialog
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(programId: Long, onBack: () -> Unit, onOpenDay: (Long) -> Unit, onStartWorkout: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val program by repo.observeProgram(programId).collectAsStateWithLifecycle(null)
    val days by repo.observeDays(programId).collectAsStateWithLifecycle(emptyList())
    val active by repo.observeActiveSession().collectAsStateWithLifecycle(null)
    var addingDay by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(program) {
        if (program != null) loaded = true else if (loaded) onBack()
    }
    val p = program ?: return

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit name & notes") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete program", color = Brand.Danger) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Brand.Danger) },
                                onClick = { menu = false; confirmDelete = true })
                        }
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
                Eyebrow("Program", color = Brand.Volt)
                Text(p.name, style = MaterialTheme.typography.displaySmall)
                if (p.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(p.notes, color = Brand.TextMid, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(days, key = { _, d -> d.id }) { index, day ->
                DayRow(
                    index = index,
                    day = day,
                    isFirst = index == 0,
                    isLast = index == days.lastIndex,
                    canStart = active == null && day.exerciseCount > 0,
                    onOpen = { onOpenDay(day.id) },
                    onStart = { scope.launch { container.startFromDay(day.id); onStartWorkout() } },
                    onMove = { delta -> scope.launch { repo.moveDay(day.id, delta) } },
                    onRename = { name -> scope.launch { repo.renameDay(day.id, name) } },
                    onDelete = { scope.launch { repo.deleteDay(day.id) } },
                )
            }
            item {
                OutlinedButton(onClick = { addingDay = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add training day")
                }
            }
        }
    }

    if (addingDay) {
        TextPromptDialog(
            title = "New training day", label = "Day name (e.g. Push, Leg Day)", initial = "Day ${days.size + 1}",
            confirmText = "Add",
            onDismiss = { addingDay = false },
            onConfirm = { name -> addingDay = false; scope.launch { onOpenDay(repo.addDay(programId, name)) } },
        )
    }
    if (renaming) {
        ProgramEditDialog(p.name, p.notes, onDismiss = { renaming = false }) { name, notes ->
            renaming = false
            scope.launch { repo.updateProgram(programId, name, notes) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${p.name}?",
            message = "The program and its days will be deleted. Workouts you already logged stay in your history.",
            confirmText = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; scope.launch { repo.deleteProgram(programId) } },
        )
    }
}

@Composable
private fun DayRow(
    index: Int,
    day: DaySummary,
    isFirst: Boolean,
    isLast: Boolean,
    canStart: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onMove: (Int) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Panel(onClick = onOpen, contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.headlineMedium.merge(Numeric),
                color = Brand.Volt.copy(alpha = 0.6f),
                modifier = Modifier.width(52.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(day.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (day.exerciseCount == 0) "No exercises yet. Tap to add." else "${day.exerciseCount} exercises",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextMid,
                )
            }
            FilledIconButton(
                onClick = onStart, enabled = canStart, shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand.Volt, contentColor = Brand.Ink),
                modifier = Modifier.size(44.dp),
            ) { Icon(Icons.Rounded.PlayArrow, "Start ${day.name}") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Day options", tint = Brand.TextMid) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Delete", color = Brand.Danger) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
    }
    if (renaming) {
        TextPromptDialog(title = "Rename day", initial = day.name, onDismiss = { renaming = false }) {
            renaming = false; onRename(it)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${day.name}?", message = "Logged workouts from this day stay in your history.",
            confirmText = "Delete", onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; onDelete() },
        )
    }
}

@Composable
private fun ProgramEditDialog(name: String, notes: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var t by remember { mutableStateOf(notes) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit program") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(t, { t = it }, label = { Text("Notes") }, minLines = 2)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(n, t) }, enabled = n.isNotBlank()) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
