package com.codyphil.liftlog.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.BodyMetric
import com.codyphil.liftlog.data.MetricKind
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.ConfirmDialog
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.NumberField
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.SectionHeader
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Converts a stored value (kg / cm / %) to the display unit. */
internal fun BodyMetric.display(v: Double, unit: WeightUnit): Double = when (kind) {
    MetricKind.WEIGHT -> Units.fromKg(v, unit)
    MetricKind.LENGTH -> Units.lengthFromCm(v, unit)
    MetricKind.PERCENT -> v
}

internal fun BodyMetric.store(v: Double, unit: WeightUnit): Double = when (kind) {
    MetricKind.WEIGHT -> Units.toKg(v, unit)
    MetricKind.LENGTH -> Units.lengthToCm(v, unit)
    MetricKind.PERCENT -> v
}

internal fun BodyMetric.unitLabel(unit: WeightUnit): String = when (kind) {
    MetricKind.WEIGHT -> unit.label
    MetricKind.LENGTH -> Units.lengthLabel(unit)
    MetricKind.PERCENT -> "%"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val unit = settings.unit
    val scope = rememberCoroutineScope()
    var metric by rememberSaveable { mutableStateOf(BodyMetric.BODY_WEIGHT) }
    val entries by repo.observeBody(metric).collectAsStateWithLifecycle(emptyList())
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val label = metric.unitLabel(unit)
    fun fmt(v: Double) = Units.formatNumber(Math.round(metric.display(v, unit) * 10) / 10.0)

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Log ${metric.label.lowercase()}") },
                containerColor = Brand.Volt, contentColor = Brand.Ink,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Eyebrow("Body", color = Brand.Volt)
                Text("Measurements", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyMetric.entries.forEach { m ->
                        FilterChip(
                            selected = m == metric, onClick = { metric = m }, label = { Text(m.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Brand.Volt, selectedLabelColor = Brand.Ink),
                        )
                    }
                }
            }
            item {
                Panel(contentPadding = PaddingValues(16.dp)) {
                    if (entries.isEmpty()) {
                        Text("No ${metric.label.lowercase()} entries yet.", color = Brand.TextMid)
                        Text("Tap “Log” to add your first one.", style = MaterialTheme.typography.bodySmall, color = Brand.TextLow)
                    } else {
                        val latest = entries.last()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmt(latest.value), style = MaterialTheme.typography.displayMedium.merge(Numeric))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.titleMedium, color = Brand.TextMid, modifier = Modifier.padding(bottom = 10.dp))
                            Spacer(Modifier.weight(1f))
                            if (entries.size > 1) {
                                val delta = metric.display(latest.value, unit) - metric.display(entries.first().value, unit)
                                Text(
                                    (if (delta >= 0) "▲ " else "▼ ") + Units.formatNumber(Math.round(abs(delta) * 10) / 10.0) + " $label",
                                    color = Brand.TextHi, style = MaterialTheme.typography.titleSmall.merge(Numeric),
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                        }
                        if (entries.size > 1) {
                            LineChart(
                                points = entries.map { ChartPoint(it.recordedAt, it.value) },
                                formatY = { "${fmt(it)} $label" },
                                formatX = Format::dateShort,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            if (entries.isNotEmpty()) item { SectionHeader("History") }
            items(entries.reversed(), key = { it.id }) { e ->
                Panel(contentPadding = PaddingValues(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${fmt(e.value)} $label", style = MaterialTheme.typography.titleMedium.merge(Numeric))
                            Text(Format.weekdayDate(e.recordedAt) + " · " + Format.time(e.recordedAt),
                                style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                        }
                        IconButton(onClick = { deleting = e.id }) { Icon(Icons.Rounded.Delete, "Delete entry", tint = Brand.TextLow) }
                    }
                }
            }
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Log ${metric.label.lowercase()}") },
            text = {
                Column {
                    NumberField(text, { text = it }, label = metric.label, suffix = label, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Saved with today's date and time.", style = MaterialTheme.typography.bodySmall, color = Brand.TextLow)
                }
            },
            confirmButton = {
                val v = Units.parse(text)
                TextButton(enabled = v != null && v > 0, onClick = {
                    adding = false
                    scope.launch { repo.addBodyEntry(metric, metric.store(v!!, unit)) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = "Delete this entry?", message = "This can't be undone.", confirmText = "Delete",
            onDismiss = { deleting = null }, onConfirm = { deleting = null; scope.launch { repo.deleteBodyEntry(id) } },
        )
    }
}
