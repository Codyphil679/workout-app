package com.codyphil.liftlog.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
                        Text("Default rest", style = MaterialTheme.typography.titleMedium)
                        Text("For exercises added on the fly.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
                    }
                    Stepper("", settings.defaultRestSeconds, { v -> container.settings.update { it.copy(defaultRestSeconds = v) } },
                        step = 15, min = 0, max = 900, display = { Format.duration(it.toLong()) })
                }
            }
        }
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
