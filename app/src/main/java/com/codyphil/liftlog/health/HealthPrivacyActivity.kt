package com.codyphil.liftlog.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codyphil.liftlog.ui.components.Eyebrow
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.LiftLogTheme

/** Required by Health Connect: explains what LiftLog does with health data. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiftLogTheme {
                Column(
                    Modifier.fillMaxSize().background(Brand.Ink).systemBarsPadding().padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Eyebrow("Privacy", color = Brand.Volt)
                    Text("LiftLog and Health Connect", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "If you turn on Health Connect sync, LiftLog writes each finished workout to Health Connect " +
                            "as a strength-training session: start and end time, workout name, and each set's exercise and reps. " +
                            "A text summary of weights is included in the session notes.\n\n" +
                            "LiftLog only asks to WRITE workouts. It doesn't read any of your other health data.\n\n" +
                            "LiftLog has no account, no servers and no analytics. Everything stays on this phone. " +
                            "You can revoke access any time in Settings → Health Connect, or turn sync off in LiftLog's settings.",
                        style = MaterialTheme.typography.bodyLarge, color = Brand.TextMid,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { finish() }) { Text("Got it") }
                }
            }
        }
    }
}
