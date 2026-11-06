package com.codyphil.liftlog

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.codyphil.liftlog.domain.WeightUnit
import com.codyphil.liftlog.ui.components.PlateCalculatorDialog
import com.codyphil.liftlog.ui.theme.Brand
import com.codyphil.liftlog.ui.theme.LiftLogTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Dialogs containing a text field never report "idle" under Robolectric at this screen size
 * (reproduced with a stock Material OutlinedTextField), which the Compose test rule waits for.
 * So these are rendered in a bare activity and the looper is advanced for a fixed time instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xhdpi", application = LiftLogApp::class)
class DialogScreenshotTest {
    @Test fun readiness() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            LiftLogTheme {
                Box(Modifier.fillMaxSize().background(Brand.Ink)) {
                    com.codyphil.liftlog.ui.components.ReadinessDialog(onDismiss = {}, onSkip = {}, onStart = { _, _ -> })
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        captureScreenRoboImage("build/outputs/roborazzi/16_readiness.png")
    }

    @Test fun plateCalculator() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            LiftLogTheme {
                Box(Modifier.fillMaxSize().background(Brand.Ink)) {
                    PlateCalculatorDialog(235.0, 45.0, WeightUnit.LB, onDismiss = {})
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        captureScreenRoboImage("build/outputs/roborazzi/09_plate_calculator.png")
    }
}
