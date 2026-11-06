package com.codyphil.liftlog.ui.recap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.domain.MonthRecap
import com.codyphil.liftlog.domain.Recaps
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.history.compact
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** Default recap month: last month during the first week of a month, otherwise this month so far. */
fun defaultRecapMonth(now: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val start = Recaps.monthStart(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
    return if (c.get(Calendar.DAY_OF_MONTH) <= 7) Recaps.previousMonth(start) else start
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(onBack: () -> Unit, initialMonth: Long = defaultRecapMonth()) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by rememberSaveable { mutableLongStateOf(initialMonth) }
    var recap by remember { mutableStateOf<MonthRecap?>(null) }
    val today = com.codyphil.liftlog.ui.components.rememberToday()
    val thisMonth = remember(today) { Calendar.getInstance().let { Recaps.monthStart(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) } }
    val layer = rememberGraphicsLayer()

    LaunchedEffect(month) {
        recap = null
        recap = withContext(Dispatchers.IO) { Recaps.build(container.repository.recapRows(Recaps.nextMonth(month)), month) }
    }

    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
                actions = {
                    IconButton(onClick = { month = Recaps.previousMonth(month) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                    IconButton(onClick = { month = Recaps.nextMonth(month) }, enabled = month < thisMonth) {
                        Icon(Icons.Rounded.ChevronRight, "Next month")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val r = recap
            if (r == null) {
                Spacer(Modifier.height(80.dp)); CircularProgressIndicator()
            } else {
                Box(
                    Modifier.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                ) { RecapCard(r, settings.unit) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap, "LiftLog ${Recaps.monthName(r.monthStart)}")
                        }
                    },
                    enabled = r.workouts > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share recap image")
                }
                Text(
                    "The image is made on your phone. Nothing is uploaded unless you choose an app to share it with.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun RecapCard(r: MonthRecap, unit: WeightUnit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.width(360.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1F2A10), Color(0xFF0E1620), Color(0xFF0A0C0F))))
            .border(1.dp, Brand.Line, shape)
            .padding(24.dp),
    ) {
        Eyebrow("Monthly recap", color = Brand.Volt)
        Text(Recaps.monthName(r.monthStart), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        if (r.workouts == 0) {
            Text("No workouts logged this month.", color = Brand.TextMid)
            return@Column
        }
        Row(Modifier.fillMaxWidth()) {
            BigStat("Workouts", r.workouts.toString(), Modifier.weight(1f), Brand.Volt,
                sub = (r.workouts - r.previousMonthWorkouts).let { d -> if (d == 0) "same as last month" else (if (d > 0) "+$d" else "$d") + " vs last month" })
            BigStat("Hours", Units.formatNumber(Math.round(r.totalMinutes / 6.0) / 10.0), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            BigStat("Sets", r.sets.toString(), Modifier.weight(1f))
            BigStat("Lifted", compact(Units.fromKg(r.volumeKg, unit)) + " " + unit.label, Modifier.weight(1f), Brand.Ice)
        }
        Spacer(Modifier.height(18.dp))
        if (r.prs.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = Brand.Ember)
                Spacer(Modifier.width(6.dp))
                Eyebrow("${r.prs.size} new est. 1RM record${if (r.prs.size == 1) "" else "s"}", color = Brand.Ember)
            }
            Spacer(Modifier.height(6.dp))
            r.prs.take(3).forEach { pr ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(pr.exerciseName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Units.formatWhole(pr.previousKg, unit)} → ${Units.formatWhole(pr.e1rmKg, unit)} ${unit.label}",
                        style = MaterialTheme.typography.titleSmall.merge(Numeric), color = Brand.Volt,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.topExercise?.let { Fact("Most trained lift", "${it.first} · ${it.second} sets") }
            r.topMuscle?.let { Fact("Top muscle", "${it.first.label} · ${it.second} sets") }
            r.favoriteWeekday?.let { Fact("Favorite day", it) }
            if (r.longestWeekStreak >= 2) Fact("Week streak", "${r.longestWeekStreak} weeks in a row")
        }
        Spacer(Modifier.height(18.dp))
        Text("LiftLog", style = MaterialTheme.typography.labelLarge, color = Brand.TextLow)
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier, color: Color = Brand.TextHi, sub: String? = null) {
    Column(modifier) {
        Eyebrow(label)
        Text(value, style = MaterialTheme.typography.displaySmall.merge(Numeric), color = color, maxLines = 1)
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.TextLow) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Brand.TextMid, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private suspend fun shareImage(context: Context, bitmap: Bitmap, title: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "recaps").apply { mkdirs() }
        val file = File(dir, "liftlog-recap.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share recap"))
}
