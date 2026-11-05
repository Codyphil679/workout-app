package com.codyphil.liftlog.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Insights
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.EmptyState
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.IconBadge
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.theme.Brand

@Composable
fun ProgressScreen(onOpenExercise: (Long) -> Unit) {
    val repo = appContainer().repository
    val list by repo.observeExercisesWithHistory().collectAsStateWithLifecycle(null)
    var query by remember { mutableStateOf("") }
    val items = list ?: return
    if (items.isEmpty()) {
        EmptyState(
            Icons.Rounded.Insights, "No progress to show yet",
            "Log a workout and each exercise gets charts and personal records here.",
            Modifier.statusBarsPadding(),
        )
        return
    }
    val filtered = items.filter { it.name.contains(query.trim(), ignoreCase = true) }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Eyebrow("Historian", color = Brand.Volt)
            Text("Progress", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search exercises") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
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
