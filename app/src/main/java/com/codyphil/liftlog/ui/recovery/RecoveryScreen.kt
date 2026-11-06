package com.codyphil.liftlog.ui.recovery

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codyphil.liftlog.domain.MuscleRecovery
import com.codyphil.liftlog.domain.Recovery
import com.codyphil.liftlog.ui.appContainer
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.components.Panel
import com.codyphil.liftlog.ui.components.Pill
import com.codyphil.liftlog.ui.theme.Brand
import kotlinx.coroutines.delay

private const val WEEK = 7 * 86_400_000L

/** Recovery for every muscle, recomputed every minute. */
@Composable
fun rememberRecovery(): List<MuscleRecovery> {
    val repo = appContainer().repository
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val since = remember(now / 3_600_000) { now - WEEK }
    val events by repo.observeMuscleEvents(since).collectAsStateWithLifecycle(emptyList())
    return remember(events, now) { Recovery.compute(events, now) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(onBack: () -> Unit) {
    val recovery = rememberRecovery()
    val now = System.currentTimeMillis()
    Scaffold(
        containerColor = Brand.Ink,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Ink),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {},
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Eyebrow("Recovery", color = Brand.Volt)
                Text("What's ready to train", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Panel {
                    BodyMap(recovery.associate { it.muscle to it.status })
                    Spacer(Modifier.height(12.dp))
                    RecoveryLegend()
                }
            }
            items(recovery.sortedBy { it.status.ordinal }, key = { it.muscle }) { r ->
                Panel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.muscle.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (r.lastTrained == null) "No hard sets in the last week"
                                else "${r.recentSets} hard sets in 3 days · last trained ${Recovery.ago(r.lastTrained, now).lowercase()}",
                                style = MaterialTheme.typography.bodySmall, color = Brand.TextMid,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill(r.status.label, color = r.status.color())
                    }
                }
            }
            item {
                Text(
                    "How this works: Recovering = trained in the last 24 hours, or 6+ hard sets in the last 48. " +
                        "Fresh = no hard sets in 4+ days. Ready = everything in between. It's a simple rule of thumb from your log, " +
                        "not a measurement. If something still feels beat up, trust that over the color.",
                    style = MaterialTheme.typography.bodySmall, color = Brand.TextLow,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}
