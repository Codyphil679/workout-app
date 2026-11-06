package com.codyphil.liftlog.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.Templates
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.IconBadge
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.ui.components.SectionHeader
import com.codyphil.liftlog.ui.components.TextPromptDialog
import com.codyphil.liftlog.ui.theme.Brand
import kotlinx.coroutines.launch

@Composable
fun ProgramsScreen(onOpenProgram: (Long) -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val programs by remember { repo.observePrograms() }.collectAsStateWithLifecycle(null)
    var creating by remember { mutableStateOf(false) }
    var creatingTemplate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Brand.Ink,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New program") },
                containerColor = Brand.Volt,
                contentColor = Brand.Ink,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Eyebrow("Build", color = Brand.Volt)
                Text("Programs", style = MaterialTheme.typography.displaySmall)
            }
            val list = programs
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        "A program is a set of training days. Each day lists exercises with target sets, reps, weight and rest.",
                        color = Brand.TextMid, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { p ->
                Panel(onClick = { onOpenProgram(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.ViewAgenda)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            if (p.notes.isNotBlank()) {
                                Text(p.notes, style = MaterialTheme.typography.bodySmall, color = Brand.TextMid, maxLines = 2)
                            }
                            Spacer(Modifier.height(6.dp))
                            Pill("${p.dayCount} day${if (p.dayCount == 1) "" else "s"}")
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.TextLow)
                    }
                }
            }
            item {
                SectionHeader("Program library")
                Text("Tap one to add a copy you can edit. Progression, warm-ups and supersets are already set up.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
            }
            items(Templates.all) { t ->
                Panel(onClick = {
                    if (!creatingTemplate) {
                        creatingTemplate = true
                        scope.launch {
                            try { onOpenProgram(repo.createFromTemplate(t, settings.unit)) } finally { creatingTemplate = false }
                        }
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.AutoAwesome, tint = Brand.Ember)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill(t.focus, color = Brand.Ember)
                                Pill(t.daysPerWeek, color = Brand.TextMid)
                            }
                        }
                        Icon(Icons.Rounded.Add, "Add template", tint = Brand.Ember)
                    }
                }
            }
        }
    }

    if (creating) {
        TextPromptDialog(
            title = "New program", label = "Program name", confirmText = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch { onOpenProgram(repo.createProgram(name)) }
            },
        )
    }
}
