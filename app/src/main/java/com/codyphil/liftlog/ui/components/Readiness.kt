package com.codyphil.liftlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.domain.Readiness
import com.codyphil.liftlog.domain.Units
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.Numeric
import kotlinx.coroutines.launch

/**
 * Returns a function that starts a program day. When the readiness check-in is on, it first asks
 * four quick questions and offers a lighter day if the answers are low. You always choose.
 */
@Composable
fun rememberDayStarter(onStarted: () -> Unit): (Long) -> Unit {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pendingDay by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }

    fun start(dayId: Long, intensity: Double, readiness: Double?) {
        scope.launch { container.startFromDay(dayId, intensity, readiness); onStarted() }
    }

    pendingDay?.let { dayId ->
        ReadinessDialog(
            onDismiss = { pendingDay = null },
            onSkip = { pendingDay = null; start(dayId, 1.0, null) },
            onStart = { score, lighter ->
                pendingDay = null
                start(dayId, if (lighter) Readiness.LIGHTER_DAY_INTENSITY else 1.0, score)
            },
        )
    }
    return { dayId ->
        scope.launch {
            // A workout already running: just resume it, no check-in.
            if (!settings.readinessCheck || container.repository.hasActiveSession()) start(dayId, 1.0, null)
            else pendingDay = dayId
        }
    }
}

@Composable
fun ReadinessDialog(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onStart: (score: Double, lighter: Boolean) -> Unit,
) {
    val answers = remember { mutableStateListOf<Int?>(null, null, null, null) }
    val complete = answers.all { it != null }
    val score = if (complete) Readiness.score(answers.map { it!! }) else null
    val lowDay = score != null && Readiness.suggestLighter(score)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you today?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Readiness.questions.forEachIndexed { qi, (question, labels) ->
                    Column {
                        Eyebrow(question)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            labels.forEachIndexed { i, label ->
                                val selected = answers[qi] == i + 1
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Brand.Volt else Brand.PanelHigher)
                                        .border(1.dp, if (selected) Brand.Volt else Brand.Line, RoundedCornerShape(10.dp))
                                        .clickable { answers[qi] = i + 1 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                        color = if (selected) Brand.Ink else Brand.TextMid, textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
                if (score != null) {
                    Text(
                        "Readiness ${Units.formatNumber(Math.round(score * 10) / 10.0)} / 5" +
                            if (lowDay) ". A lighter day (−10% on every working set) might be smart. Lighter days don't count against your progression." else ".",
                        style = MaterialTheme.typography.bodySmall.merge(Numeric),
                        color = if (lowDay) Brand.Ember else Brand.TextMid,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lowDay) {
                    Button(onClick = { onStart(score!!, true) }, colors = ButtonDefaults.buttonColors(containerColor = Brand.Ember)) {
                        Text("Lighter day (−10%)")
                    }
                    OutlinedButton(onClick = { onStart(score!!, false) }) { Text("Train as planned") }
                } else {
                    Button(onClick = { onStart(score ?: 0.0, false) }, enabled = complete) { Text("Start workout") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip", color = Brand.TextMid) } },
    )
}
