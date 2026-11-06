package com.codyphil.liftlog.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.health.HealthSync
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.ConfirmDialog
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.IconBadge
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.SectionHeader
import com.codyphil.liftlog.ui.components.Stepper
import com.codyphil.liftlog.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runIo(context, "Backup saved") { writeText(context, uri, container.backup.exportJson()) }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runIo(context, "CSV exported") { writeText(context, uri, container.backup.exportCsv(settings.unit)) }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Eyebrow("Preferences", color = Brand.Volt)
            Text("Settings", style = MaterialTheme.typography.displaySmall)
        }
        item {
            Panel {
                Text("Weight unit", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WeightUnit.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = settings.unit == u,
                            onClick = { container.settings.update { it.copy(unit = u) } },
                            shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Brand.Volt, activeContentColor = Brand.Ink),
                        ) { Text(if (u == WeightUnit.LB) "Pounds (lb)" else "Kilograms (kg)") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Switching converts how weights are shown. Your logged data isn't changed.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextLow)
                HorizontalDivider(Modifier.padding16(), color = Brand.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-start rest timer", style = MaterialTheme.typography.titleMedium)
                        Text("Starts when you check off a set.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Switch(
                        checked = settings.autoStartRest,
                        onCheckedChange = { v -> container.settings.update { it.copy(autoStartRest = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.Volt, checkedThumbColor = Brand.Ink),
                    )
                }
                HorizontalDivider(Modifier.padding16(), color = Brand.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Track RPE", style = MaterialTheme.typography.titleMedium)
                        Text("Adds an effort column (6–10) to each set.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Switch(
                        checked = settings.trackRpe,
                        onCheckedChange = { v -> container.settings.update { it.copy(trackRpe = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.Volt, checkedThumbColor = Brand.Ink),
                    )
                }
                HorizontalDivider(Modifier.padding16(), color = Brand.Line)
                Text("Empty bar weight", style = MaterialTheme.typography.titleMedium)
                Text("Used by the plate calculator and warm-up sets.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = if (settings.unit == WeightUnit.LB) listOf(45.0, 35.0, 25.0, 15.0) else listOf(20.0, 15.0, 10.0)
                    options.forEach { b ->
                        Pill(
                            "${Units.formatNumber(b)} ${settings.unit.label}",
                            color = if (b == settings.bar) Brand.Volt else Brand.TextMid,
                            modifier = Modifier.clickable {
                                container.settings.update {
                                    if (it.unit == WeightUnit.LB) it.copy(barLb = b) else it.copy(barKg = b)
                                }
                            },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding16(), color = Brand.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Default rest", style = MaterialTheme.typography.titleMedium)
                        Text("For exercises added on the fly.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Stepper("", settings.defaultRestSeconds, { v -> container.settings.update { it.copy(defaultRestSeconds = v) } },
                        step = 15, min = 0, max = 900, display = { Format.duration(it.toLong()) })
                }
            }
        }
        item { SectionHeader("Coaching") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Readiness check-in", style = MaterialTheme.typography.titleMedium)
                        Text("Four quick questions before a program day. Low score → option for a lighter day.",
                            style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Switch(
                        checked = settings.readinessCheck,
                        onCheckedChange = { v -> container.settings.update { it.copy(readinessCheck = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.Volt, checkedThumbColor = Brand.Ink),
                    )
                }
            }
        }
        item { SectionHeader("Health Connect") }
        item { HealthConnectPanel() }
        item { SectionHeader("Backup & export") }
        item {
            Panel(contentPadding = PaddingValues(vertical = 6.dp)) {
                ActionRow(Icons.Rounded.Backup, "Back up everything", "Programs + full history as a JSON file. Save it to Drive or email it to yourself.") {
                    exportJson.launch("liftlog-backup-$stamp.json")
                }
                HorizontalDivider(color = Brand.Line)
                ActionRow(Icons.Rounded.Restore, "Restore from backup", "Replaces all data on this phone with a backup file.", tint = Brand.Ember) {
                    importJson.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
                HorizontalDivider(color = Brand.Line)
                ActionRow(Icons.Rounded.TableChart, "Export sets to CSV", "Every completed set, for Excel or Google Sheets.", tint = Brand.Ice) {
                    exportCsv.launch("liftlog-sets-$stamp.csv")
                }
            }
        }
        item {
            Text("LiftLog stores everything on this device. Back up regularly. Uninstalling the app deletes your data.",
                style = MaterialTheme.typography.bodySmall, color = Brand.TextLow)
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Replace all data?",
            message = "Everything currently in LiftLog (programs, history, and any workout in progress) will be replaced by the backup.",
            confirmText = "Replace",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    runIo(context, null) {
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Couldn't read that file")
                        val r = container.backup.importJson(text)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Restored ${r.programs} programs and ${r.workouts} workouts", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )
    }
}

private fun Modifier.padding16() = this.fillMaxWidth().padding(vertical = 14.dp)

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, tint: androidx.compose.ui.graphics.Color = Brand.Volt, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        IconBadge(icon, tint = tint, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Brand.TextHi)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
        }
        Icon(Icons.Rounded.FileDownload, null, tint = Brand.TextLow)
    }
}

private fun writeText(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
        ?: error("Couldn't open that location for writing")
}

private suspend fun runIo(context: Context, success: String?, block: suspend () -> Unit) {
    val result = withContext(Dispatchers.IO) { runCatching { block() } }
    val msg = result.fold({ success }, { "Failed: ${it.message ?: it.javaClass.simpleName}" })
    if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

@Composable
private fun HealthConnectPanel() {
    val container = appContainer()
    val health = container.health
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availability = remember { health.availability() }
    var syncing by remember { mutableStateOf(false) }

    fun syncAll() {
        syncing = true
        scope.launch {
            val n = health.syncAll(settings.unit)
            syncing = false
            Toast.makeText(context, if (n > 0) "Synced $n workouts to Health Connect" else "Nothing synced", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(health.permissionContract()) { granted ->
        if (granted.containsAll(health.permissions)) {
            container.settings.update { it.copy(healthConnectSync = true) }
            syncAll()
        } else {
            Toast.makeText(context, "Health Connect access wasn't granted", Toast.LENGTH_SHORT).show()
        }
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sync workouts", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (availability) {
                        HealthSync.Availability.AVAILABLE -> "Finished workouts appear in Health Connect (and apps that read it, like Samsung Health), with each set's reps."
                        HealthSync.Availability.NEEDS_INSTALL_OR_UPDATE -> "Health Connect needs to be installed or updated from the Play Store first."
                        HealthSync.Availability.UNSUPPORTED -> "Health Connect isn't available on this device."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextMid,
                )
            }
            Switch(
                checked = settings.healthConnectSync,
                enabled = availability == HealthSync.Availability.AVAILABLE,
                onCheckedChange = { on ->
                    if (!on) container.settings.update { it.copy(healthConnectSync = false) }
                    else scope.launch {
                        if (health.hasPermission()) {
                            container.settings.update { it.copy(healthConnectSync = true) }
                            syncAll()
                        } else permissionLauncher.launch(health.permissions)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Brand.Volt, checkedThumbColor = Brand.Ink),
            )
        }
        if (availability == HealthSync.Availability.NEEDS_INSTALL_OR_UPDATE) {
            TextButton(onClick = { runCatching { context.startActivity(health.installIntent()) } }) { Text("Get Health Connect") }
        }
        if (settings.healthConnectSync) {
            TextButton(onClick = { syncAll() }, enabled = !syncing) {
                Text(if (syncing) "Syncing…" else "Sync all past workouts again")
            }
        }
    }
}
