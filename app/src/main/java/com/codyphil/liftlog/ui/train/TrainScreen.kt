package com.codyphil.liftlog.ui.train

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.data.DayWithProgram
import com.codyphil.liftlog.data.SessionSummary
import com.codyphil.liftlog.domain.Format
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.IconBadge
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.ui.components.SectionHeader
import com.codyphil.liftlog.ui.components.StatTile
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun TrainScreen(onOpenWorkout: () -> Unit, onOpenPrograms: () -> Unit, onOpenSession: (Long) -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val active by repo.observeActiveSession().collectAsStateWithLifecycle(null)
    val days by repo.observeAllDays().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.observeFinishedSessions().collectAsStateWithLifecycle(emptyList())

    val weekStart = remember { startOfWeek() }
    val thisWeek = sessions.filter { it.startedAt >= weekStart }
    val upNext = remember(days, sessions) { upNext(days, sessions) }

    fun start(block: suspend () -> Unit) {
        scope.launch { block(); onOpenWorkout() }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                Eyebrow(greeting(), color = Brand.Volt)
                Text("Let’s lift.", style = MaterialTheme.typography.displayMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("This week", thisWeek.size.toString(), Modifier.weight(1f), accent = Brand.Volt, unit = "workouts")
                StatTile(
                    "Week volume",
                    Units.formatVolume(thisWeek.sumOf { it.volumeKg }, settings.unit).substringBeforeLast(' '),
                    Modifier.weight(1f),
                    unit = settings.unit.label,
                )
            }
        }

        val current = active
        if (current != null) {
            item { ActiveWorkoutCard(current.name, current.startedAt, onOpenWorkout) }
        } else {
            if (upNext != null) {
                item {
                    Panel(brush = Brand.heroGradient, contentPadding = PaddingValues(20.dp)) {
                        Eyebrow("Up next", color = Brand.Volt)
                        Spacer(Modifier.height(6.dp))
                        Text(upNext.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${upNext.programName} · ${upNext.exerciseCount} exercises",
                            style = MaterialTheme.typography.bodyMedium, color = Brand.TextMid,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { start { repo.startFromDay(upNext.id) } },
                            enabled = upNext.exerciseCount > 0,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start workout", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { start { repo.startEmpty() } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.Bolt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Quick workout (no program)")
                }
            }
        }

        item {
            SectionHeader("Your program days")
        }
        if (days.isEmpty()) {
            item {
                Panel(onClick = onOpenPrograms) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.ViewAgenda)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Build your first program", style = MaterialTheme.typography.titleMedium)
                            Text("Set up days, exercises, sets, reps and rest.", color = Brand.TextMid, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Brand.Volt)
                    }
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(days, key = { it.id }) { day ->
                        DayCard(day, enabled = current == null && day.exerciseCount > 0) {
                            start { repo.startFromDay(day.id) }
                        }
                    }
                    item {
                        Panel(Modifier.width(150.dp).height(156.dp), onClick = onOpenPrograms) {
                            Spacer(Modifier.weight(1f))
                            IconBadge(Icons.Rounded.Add, size = 36.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Edit programs", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item { SectionHeader("Recent") }
            items(sessions.take(3), key = { it.id }) { s -> RecentRow(s, settings.unit) { onOpenSession(s.id) } }
        }
    }
}

@Composable
private fun ActiveWorkoutCard(name: String, startedAt: Long, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot",
    )
    Panel(brush = Brand.heroGradient, onClick = onOpen, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(Brand.Volt))
            Spacer(Modifier.width(8.dp))
            Eyebrow("Workout in progress", color = Brand.Volt)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            Format.duration((now - startedAt) / 1000),
            style = MaterialTheme.typography.displaySmall.merge(Numeric),
            color = Brand.TextHi,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Resume", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DayCard(day: DayWithProgram, enabled: Boolean, onStart: () -> Unit) {
    Panel(Modifier.width(190.dp).height(156.dp)) {
        Eyebrow(day.programName, color = Brand.TextLow)
        Spacer(Modifier.height(4.dp))
        Text(day.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${day.exerciseCount} exercises", style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
        Spacer(Modifier.weight(1f))
        FilledTonalButton(
            onClick = onStart,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Brand.PanelHigher, contentColor = Brand.Volt),
            modifier = Modifier.height(36.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start")
        }
    }
}

@Composable
private fun RecentRow(s: SessionSummary, unit: com.codyphil.liftlog.domain.WeightUnit, onClick: () -> Unit) {
    Panel(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.CalendarMonth, tint = Brand.Ice, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.weekdayDate(s.startedAt), style = MaterialTheme.typography.bodySmall, color = Brand.TextMid)
            }
            Pill(Units.formatVolume(s.volumeKg, unit), color = Brand.Ice)
        }
    }
}

/** The day after the most recently trained day of the most recently used program. */
private fun upNext(days: List<DayWithProgram>, sessions: List<SessionSummary>): DayWithProgram? {
    if (days.isEmpty()) return null
    val byId = days.associateBy { it.id }
    val lastDay = sessions.firstNotNullOfOrNull { s -> s.programDayId?.let { byId[it] } }
        ?: return days.firstOrNull { it.exerciseCount > 0 } ?: days.first()
    val programDays = days.filter { it.programId == lastDay.programId }.sortedBy { it.position }
    val idx = programDays.indexOfFirst { it.id == lastDay.id }
    return programDays[(idx + 1) % programDays.size]
}

private fun startOfWeek(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    if (timeInMillis > System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, -1)
}.timeInMillis

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
